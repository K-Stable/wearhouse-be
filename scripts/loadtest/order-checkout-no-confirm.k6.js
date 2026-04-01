import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'https://api.wear-house.shop';
const loginId = __ENV.LOGIN_ID || '';
const password = __ENV.PASSWORD || '';
const orderMode = (__ENV.ORDER_MODE || 'member').toLowerCase(); // member | guest
const rate = Number(__ENV.RATE || '200');
const vus = Number(__ENV.VUS || '100');
const duration = __ENV.DURATION || '3m';
const timeout = __ENV.TIMEOUT || '30s';
const paymentMethod = __ENV.PAYMENT_METHOD || (orderMode === 'guest' ? 'CARD' : 'STABLE');
const optionIds = (__ENV.OPTION_IDS || '1')
  .split(',')
  .map((v) => Number(v.trim()))
  .filter((v) => Number.isInteger(v) && v > 0);

const createSuccess = new Counter('order_create_success_total');
const createFailure = new Counter('order_create_failure_total');
const createErrorRate = new Rate('order_create_error_rate');
const createLatency = new Trend('order_create_latency_ms', true);
const checkoutUrlMissing = new Counter('order_checkout_url_missing_total');

export const options = {
  discardResponseBodies: false,
  scenarios: {
    checkout_prepare_load: {
      executor: 'constant-arrival-rate',
      rate,
      timeUnit: '1s',
      duration,
      preAllocatedVUs: vus,
      maxVUs: vus,
      tags: { flow: 'order_checkout_prepare' },
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.99'],
    http_req_duration: ['p(95)<5000'],
    order_create_error_rate: ['rate<0.99'],
    order_create_latency_ms: ['p(99)<8000'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

export function setup() {
  if (orderMode === 'guest') {
    return { token: '' };
  }

  if (!loginId || !password) {
    throw new Error('LOGIN_ID and PASSWORD are required');
  }

  const loginRes = http.post(
    `${baseUrl}/auth-service/api/v1/auth/buyers/login`,
    JSON.stringify({
      loginId,
      password,
    }),
    {
      headers: { 'Content-Type': 'application/json' },
      timeout,
      tags: { name: 'buyer_login' },
    }
  );

  const ok = check(loginRes, {
    'login status is 200': (r) => r.status === 200,
  });
  if (!ok) {
    throw new Error(`login failed: status=${loginRes.status} body=${String(loginRes.body).slice(0, 300)}`);
  }

  let token = '';
  try {
    const parsed = JSON.parse(loginRes.body || '{}');
    token = parsed?.data?.accessToken || '';
  } catch (e) {
    throw new Error(`login response parse failed: ${e}`);
  }

  if (!token) {
    throw new Error('accessToken not found in login response');
  }

  return { token };
}

function buildOrderBody() {
  const suffix = `${Date.now()}-${Math.floor(Math.random() * 100000)}`;
  const optionId = optionIds[Math.floor(Math.random() * optionIds.length)] || 1;
  return {
    paymentMethod,
    recipientName: 'k6-load',
    recipientPhone: '01012345678',
    zipCode: '03031',
    address1: '서울특별시 종로구 자하문로 124',
    address2: `k6-${suffix}`,
    deliveryRequest: null,
    shippingFee: 3000,
    discountAmount: 0,
    pointUsedAmount: 0,
    items: [
      {
        productId: 1,
        optionId,
        productName: 'k6-product',
        optionName: 'Black / S',
        unitPrice: 10000,
        quantity: 1,
      },
    ],
  };
}

export default function (data) {
  const headers = { 'Content-Type': 'application/json' };
  if (orderMode !== 'guest') {
    headers.Authorization = `Bearer ${data.token}`;
  }

  const payload = JSON.stringify(buildOrderBody());
  const orderCreatePath = orderMode === 'guest'
    ? '/order-service/api/v1/buyer/guest/orders'
    : '/order-service/api/v1/buyer/orders';
  const res = http.post(`${baseUrl}${orderCreatePath}`, payload, {
    headers,
    timeout,
    tags: { name: 'buyer_order_create' },
  });

  createLatency.add(res.timings.duration);

  let parsed = null;
  try {
    parsed = JSON.parse(res.body || '{}');
  } catch (_) {
    parsed = null;
  }

  const is200 = res.status === 200;
  const responseData = parsed?.data || parsed || {};
  const hasOrderNo = Boolean(responseData.orderNo);
  const hasCheckoutUrl = Boolean(responseData.checkoutUrl);
  const isGuestMode = orderMode === 'guest';
  const isSuccess = isGuestMode ? (is200 && hasOrderNo) : (is200 && hasCheckoutUrl);

  if (isSuccess) {
    createSuccess.add(1);
  } else {
    createFailure.add(1);
    if (is200 && !hasCheckoutUrl) {
      checkoutUrlMissing.add(1);
    }
  }
  createErrorRate.add(!isSuccess);
}
