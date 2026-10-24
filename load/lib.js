import http from 'k6/http';
import encoding from 'k6/encoding';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';

export const BASE_URL = (__ENV.BASE_URL || 'http://127.0.0.1:8080').replace(/\/$/, '');
export const RUN_ID = __ENV.RUN_ID || String(Date.now());
if (!/^http:\/\/(127\.0\.0\.1|localhost|\[::1\])(:[0-9]+)?$/.test(BASE_URL)) {
  throw new Error('BASE_URL은 localhost HTTP 주소여야 합니다.');
}
if (!/^[A-Za-z0-9_-]{1,32}$/.test(RUN_ID)) {
  throw new Error('RUN_ID는 ASCII 영문·숫자·밑줄·하이픈 1~32자여야 합니다.');
}
const passwords = {
  ADMIN: __ENV.ADMIN_PASSWORD,
  USER_A: __ENV.USER_A_PASSWORD,
  USER_B: __ENV.USER_B_PASSWORD,
};
for (const username of Object.keys(passwords)) {
  if (!passwords[username] || !passwords[username].trim()) {
    throw new Error(`${username} 비밀번호 환경 변수가 필요합니다.`);
  }
}

export const unexpected = new Counter('unexpected');
export const unexpectedRate = new Rate('unexpected_rate');

export function authorization(username) {
  if (!passwords[username]) throw new Error('정의되지 않은 부하 계정입니다.');
  return `Basic ${encoding.b64encode(`${username}:${passwords[username]}`, 'std')}`;
}

export function responseJson(response) {
  try { return response.json(); } catch (_) { return null; }
}

// HTTP마다 한 번만 호출해 분모를 요청 수로 유지한다. 응답·인증 값은 출력하지 않는다.
export function classify(response, status, valid = () => true) {
  const ok = check(response, {
    '응답 상태·계약이 예상과 일치': (r) => r.status === status && valid(r),
  });
  unexpected.add(ok ? 0 : 1);
  unexpectedRate.add(!ok);
  return ok;
}

export function createResource(name, capacity) {
  const response = http.post(`${BASE_URL}/api/resources`, JSON.stringify({ name, capacity }), {
    headers: { Authorization: authorization('ADMIN'), 'Content-Type': 'application/json' },
    timeout: '20s', tags: { name: 'POST /api/resources' },
  });
  if (!classify(response, 201, (r) => {
    const body = responseJson(r);
    return body && typeof body.id === 'string' && body.capacity === capacity && body.available === capacity;
  })) throw new Error(`setup 대상 생성 실패: HTTP ${response.status}`);
  return responseJson(response).id;
}

export function createReservation(resourceId, key, username) {
  if (!/^[A-Za-z0-9_-]{1,64}$/.test(key)) throw new Error('생성 키 형식·길이가 유효하지 않습니다.');
  return http.post(`${BASE_URL}/api/reservations`, JSON.stringify({ resourceId }), {
    headers: {
      Authorization: authorization(username), 'Content-Type': 'application/json', 'Idempotency-Key': key,
    }, timeout: '20s', tags: { name: 'POST /api/reservations' },
  });
}

export function cancelReservation(id, username) {
  return http.post(`${BASE_URL}/api/reservations/${id}/cancel`, null, {
    headers: { Authorization: authorization(username) },
    timeout: '20s', tags: { name: 'POST /api/reservations/{id}/cancel' },
  });
}

export function createdBodyIsValid(response, resourceId) {
  const body = responseJson(response);
  return body && typeof body.id === 'string' && body.id.length > 0
    && body.resourceId === resourceId && body.status === 'CONFIRMED'
    && typeof body.createdAt === 'string';
}
