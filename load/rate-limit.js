import http from 'k6/http';
import { Counter } from 'k6/metrics';
import { RUN_ID, createResource, createReservation, classify, responseJson, createdBodyIsValid } from './lib.js';

const created = new Counter('created_201');
const limited = new Counter('limited_429');
export const options = {
  scenarios: { rate_limit: { executor: 'per-vu-iterations', vus: 1, iterations: 25, maxDuration: '55s' } },
  thresholds: {
    created_201: ['count==20'], limited_429: ['count==5'], unexpected: ['count==0'],
    checks: ['rate==1'], iterations: ['count==25'], iteration_duration: ['max<60000'],
  },
  summaryTrendStats: ['min', 'med', 'p(50)', 'p(95)', 'p(99)', 'max'],
};
http.setResponseCallback(http.expectedStatuses(201, 429));
export function setup() { return { resourceId: createResource(`limit-${RUN_ID}`, 100) }; }

export default function (data) {
  created.add(0); limited.add(0);
  const response = createReservation(data.resourceId, `${RUN_ID}-limit-${__ITER}`, 'USER_A');
  if (__ITER < 20) {
    if (response.status === 201) created.add(1);
    classify(response, 201, (r) => createdBodyIsValid(r, data.resourceId));
  } else {
    const validLimit = (r) => responseJson(r)?.code === 'RATE_LIMITED'
      && /^[1-9][0-9]*$/.test(String(r.headers['Retry-After'] || ''));
    if (response.status === 429 && validLimit(response)) limited.add(1);
    classify(response, 429, validLimit);
  }
}
