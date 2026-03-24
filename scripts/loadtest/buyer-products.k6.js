import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const buyerProductsErrors = new Rate('buyer_products_errors');
const buyerProductsLatency = new Trend('buyer_products_latency', true);

const vus = Number(__ENV.VUS || '50');
const duration = __ENV.DURATION || '1m';
const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:8000';
const requestPath = __ENV.REQUEST_PATH || '/api/v1/buyer/products?limit=20';

export const options = {
  vus,
  duration,
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<1200'],
    buyer_products_errors: ['rate<0.05'],
    buyer_products_latency: ['p(95)<1200'],
  },
};

export default function () {
  const response = http.get(`${baseUrl}${requestPath}`, {
    tags: {
      flow: 'buyer_products',
    },
  });

  const ok = check(response, {
    'status is 200': (r) => r.status === 200,
  });

  if (__ENV.DEBUG === '1') {
    const preview = response.body ? String(response.body).slice(0, 200) : '';
    console.log(
      `status=${response.status} duration_ms=${response.timings.duration} body=${preview}`
    );
  }

  buyerProductsErrors.add(!ok);
  buyerProductsLatency.add(response.timings.duration);

  // Simulate browsing think-time between interactions.
  sleep(2 + Math.random() * 3);
}
