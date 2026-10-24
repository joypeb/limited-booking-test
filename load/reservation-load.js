import http from 'k6/http';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';
import {
  RUN_ID, createResource, createReservation, cancelReservation,
  classify, responseJson, createdBodyIsValid,
} from './lib.js';

const scenario = __ENV.SCENARIO;
if (!['low', 'high', 'replay'].includes(scenario)) throw new Error('SCENARIO는 low|high|replay여야 합니다.');
const created = new Counter('created_201');
const soldOut = new Counter('sold_out_409');
const cancelledCount = new Counter('cancelled_200');
const idMismatch = new Counter('id_mismatch');
const httpSuccess = new Counter('http_success');
const firstIds = new Map(); // VU별 독립 JS 런타임의 최초 예약 id.

function expectedCount(name, fallback) {
  const value = __ENV[name] || String(fallback);
  if (!/^\d+$/.test(value)) throw new Error(`${name}은 0 이상의 정수여야 합니다.`);
  return value;
}

const configurations = {
  low: {
    scenarios: {
      low: { executor: 'constant-vus', vus: 10, duration: '2m30s', exec: 'low', gracefulStop: '45s' },
    },
    thresholds: {
      'http_req_duration{phase:measure}': ['p(95)<500'],
      'unexpected_rate{phase:measure}': ['rate<0.01'],
      'checks{phase:measure}': ['rate>0.99'],
      'http_reqs{phase:measure}': ['count>0'],
    },
  },
  high: {
    scenarios: { high: { executor: 'shared-iterations', vus: 50, iterations: 2000, exec: 'high', maxDuration: '15m' } },
    thresholds: {
      created_201: [`count==${expectedCount('EXPECT_CREATED', 1000)}`],
      sold_out_409: [`count==${expectedCount('EXPECT_SOLD_OUT', 1000)}`],
      unexpected: ['count==0'], checks: ['rate==1'], iterations: ['count==2000'],
    },
  },
  replay: {
    scenarios: { replay: { executor: 'per-vu-iterations', vus: 10, iterations: 50, exec: 'replay', maxDuration: '10m' } },
    thresholds: {
      id_mismatch: ['count==0'], unexpected: ['count==0'],
      http_success: ['count==500'], checks: ['rate==1'], iterations: ['count==500'],
    },
  },
};
export const options = {
  ...configurations[scenario],
  summaryTrendStats: ['min', 'med', 'p(50)', 'p(95)', 'p(99)', 'max'],
};
if (scenario === 'high') http.setResponseCallback(http.expectedStatuses(201, 409));

export function setup() {
  const count = scenario === 'low' ? 10 : 1;
  const resources = [];
  for (let i = 0; i < count; i++) resources.push(createResource(`load-${RUN_ID}-${i}`, 1000));
  return { resources };
}

export function low(data) {
  // 경계에 걸친 반복도 시작 시각으로 분류하며 생성·취소에 같은 phase를 유지한다.
  const phase = exec.instance.currentTestRunDuration < 30000 ? 'warmup' : 'measure';
  const tags = { phase };
  const username = __VU % 2 === 1 ? 'USER_A' : 'USER_B';
  const resourceId = data.resources[(__VU + __ITER - 1) % data.resources.length];
  const key = `${RUN_ID}-${__VU}-${__ITER}-${exec.scenario.name}`;
  const response = createReservation(resourceId, key, username, tags);
  created.add(response.status === 201 ? 1 : 0, tags);
  if (!classify(response, 201, (r) => createdBodyIsValid(r, resourceId), tags)) return;
  const id = responseJson(response).id;
  const cancelled = cancelReservation(id, username, tags);
  cancelledCount.add(cancelled.status === 200 ? 1 : 0, tags);
  classify(cancelled, 200, (r) => {
    const body = responseJson(r);
    return body && body.id === id && body.resourceId === resourceId
      && body.status === 'CANCELLED' && typeof body.cancelledAt === 'string';
  }, tags);
}

export function high(data) {
  created.add(0); soldOut.add(0); // 0건도 threshold 평가 대상에 포함한다.
  const username = __VU % 2 === 1 ? 'USER_A' : 'USER_B';
  const resourceId = data.resources[0];
  const response = createReservation(resourceId, `${RUN_ID}-h-${__VU}-${__ITER}`, username);
  if (response.status === 201) {
    created.add(1);
    classify(response, 201, (r) => createdBodyIsValid(r, resourceId));
  } else {
    if (response.status === 409 && responseJson(response)?.code === 'SOLD_OUT') soldOut.add(1);
    classify(response, 409, (r) => responseJson(r)?.code === 'SOLD_OUT');
  }
}

export function replay(data) {
  idMismatch.add(0); httpSuccess.add(0);
  const resourceId = data.resources[0];
  const key = `${RUN_ID}-r-${__VU}-${__ITER % 5}`;
  const response = createReservation(resourceId, key, 'USER_A');
  if (response.status === 201) httpSuccess.add(1);
  const prior = firstIds.get(key);
  const body = responseJson(response);
  const mismatch = prior !== undefined && body?.id !== prior;
  if (mismatch) idMismatch.add(1);
  const ok = classify(response, 201, (r) => createdBodyIsValid(r, resourceId)
    && !mismatch && r.headers['Idempotent-Replayed'] === (prior === undefined ? 'false' : 'true'));
  if (ok && prior === undefined) firstIds.set(key, body.id);
}
