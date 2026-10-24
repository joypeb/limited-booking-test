import http from 'k6/http';
import { Counter } from 'k6/metrics';
import { RUN_ID, createResource, createReservation, classify, responseJson, createdBodyIsValid } from './lib.js';

const phase = __ENV.PHASE;
if (!['fill', 'limited'].includes(phase)) throw new Error('PHASE는 fill|limited여야 합니다.');
if (phase === 'limited' && !/^[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$/.test(__ENV.RESOURCE_ID || '')) {
  throw new Error('limited 단계에는 유효한 RESOURCE_ID가 필요합니다.');
}
const created = new Counter('created_201');
const limited = new Counter('limited_429');
const iterations = phase === 'fill' ? 20 : 5;
export const options = {
  scenarios: { rate_limit: {
    executor: 'per-vu-iterations', vus: 1, iterations,
    maxDuration: phase === 'fill' ? '45s' : '10s', gracefulStop: '0s',
  } },
  thresholds: {
    ...(phase === 'fill' ? { created_201: ['count==20'] } : { limited_429: ['count==5'] }),
    unexpected: ['count==0'], checks: ['rate==1'], iterations: [`count==${iterations}`],
  },
  summaryTrendStats: ['min', 'med', 'p(50)', 'p(95)', 'p(99)', 'max'],
};
http.setResponseCallback(http.expectedStatuses(201, 429));
export function setup() {
  return { resourceId: phase === 'fill' ? createResource(`limit-${RUN_ID}`, 100) : __ENV.RESOURCE_ID };
}

export default function (data) {
  created.add(0); limited.add(0);
  const response = createReservation(data.resourceId, `${RUN_ID}-${phase}-${__ITER}`, 'USER_A');
  if (phase === 'fill') {
    if (response.status === 201) created.add(1);
    classify(response, 201, (r) => createdBodyIsValid(r, data.resourceId));
  } else {
    const validLimit = (r) => responseJson(r)?.code === 'RATE_LIMITED'
      && /^[1-9][0-9]*$/.test(String(r.headers['Retry-After'] || ''));
    if (response.status === 429 && validLimit(response)) limited.add(1);
    classify(response, 429, validLimit);
  }
}
