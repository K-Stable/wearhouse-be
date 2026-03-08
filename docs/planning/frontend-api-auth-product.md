# Frontend API Spec (Auth + Product)

기준: 현재 `wearhouse-be` 코드 구현

## 1. 공통

### Base URL (Gateway)
- 로컬: `http://localhost:8000`

### Gateway Route Prefix
- auth-service: `/auth-service/**`
- user-service: `/user-service/**`
- product-service: `/product-service/**`

### 인증 방식
- Access/Refresh 토큰은 `HttpOnly Cookie`로 처리
- 프론트 요청은 반드시 `credentials: 'include'` (axios는 `withCredentials: true`)
- 응답은 기본적으로 `ApiResponse` 래핑

성공 공통 형태:
```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": { }
}
```

실패 공통 형태:
```json
{
  "success": false,
  "code": "...",
  "message": "...",
  "data": null
}
```

### Gateway 인증 실패 코드 (필터 직접 응답)
- `401 ACCESS_TOKEN_MISSING`
- `401 ACCESS_TOKEN_INVALID`
- `403 FORBIDDEN_SELLER_ONLY` (seller 경로를 buyer가 호출한 경우)

## 2. Auth / Signup API

## 2.1 Buyer 회원가입
- `POST /user-service/api/v1/users/buyers/signup`
- 인증 필요 없음

Request:
```json
{
  "email": "buyer@example.com",
  "password": "pass1234",
  "displayName": "Buyer Kim"
}
```

Response data:
```json
{
  "userId": 1,
  "userType": "BUYER",
  "email": "buyer@example.com",
  "accessTokenExpiresAt": "2026-03-08T20:30:00"
}
```

Set-Cookie:
- `buyer_access_token`
- `buyer_refresh_token`

## 2.2 Seller 회원가입
- `POST /user-service/api/v1/users/sellers/signup`
- 인증 필요 없음

Request:
```json
{
  "email": "seller@example.com",
  "password": "pass1234",
  "displayName": "Seller Lee"
}
```

Response data:
```json
{
  "userId": 11,
  "userType": "SELLER",
  "email": "seller@example.com",
  "accessTokenExpiresAt": "2026-03-08T20:30:00"
}
```

Set-Cookie:
- `seller_access_token`
- `seller_refresh_token`

## 2.3 Buyer 로그인
- `POST /auth-service/api/v1/auth/buyers/login`
- 인증 필요 없음

Request:
```json
{
  "email": "buyer@example.com",
  "password": "pass1234"
}
```

Response data:
```json
{
  "userId": 1,
  "userType": "BUYER",
  "email": "buyer@example.com",
  "accessTokenExpiresAt": "2026-03-08T20:30:00"
}
```

Set-Cookie:
- `buyer_access_token`
- `buyer_refresh_token`

## 2.4 Seller 로그인
- `POST /auth-service/api/v1/auth/sellers/login`
- 인증 필요 없음

Request:
```json
{
  "email": "seller@example.com",
  "password": "pass1234"
}
```

Response data:
```json
{
  "userId": 11,
  "userType": "SELLER",
  "email": "seller@example.com",
  "accessTokenExpiresAt": "2026-03-08T20:30:00"
}
```

Set-Cookie:
- `seller_access_token`
- `seller_refresh_token`

## 2.5 Buyer 토큰 재발급
- `POST /auth-service/api/v1/auth/buyers/refresh`
- `buyer_refresh_token` 쿠키 필요
- Request body 없음

Response data:
```json
{
  "userId": 1,
  "userType": "BUYER",
  "email": "buyer@example.com",
  "accessTokenExpiresAt": "2026-03-08T21:00:00"
}
```

Set-Cookie:
- `buyer_access_token` 재발급
- `buyer_refresh_token` 회전 재발급

## 2.6 Seller 토큰 재발급
- `POST /auth-service/api/v1/auth/sellers/refresh`
- `seller_refresh_token` 쿠키 필요
- Request body 없음

## 2.7 Buyer 로그아웃
- `POST /auth-service/api/v1/auth/buyers/logout`
- Request body 없음
- refresh 토큰 revoke + 쿠키 삭제

## 2.8 Seller 로그아웃
- `POST /auth-service/api/v1/auth/sellers/logout`
- Request body 없음
- refresh 토큰 revoke + 쿠키 삭제

## 3. Product API (Seller)

권한: `ROLE_SELLER` 필수

## 3.1 상품 등록
- `POST /product-service/api/v1/seller/products`

Request:
```json
{
  "name": "Gray vintage 3D computer",
  "price": 50000,
  "category": "Outer",
  "description": "2026 spring collection",
  "mainImageUrl": "https://cdn.example.com/products/main.jpg",
  "previewImageUrls": [
    "https://cdn.example.com/products/preview-1.jpg",
    "https://cdn.example.com/products/preview-2.jpg"
  ],
  "detailImageUrls": [
    "https://cdn.example.com/products/detail-1.jpg"
  ],
  "status": "PENDING",
  "options": [
    {
      "size": "Free",
      "color": "Black",
      "stockQuantity": 10,
      "additionalPrice": 0
    },
    {
      "size": "S/M/L",
      "color": "Navy",
      "stockQuantity": 5,
      "additionalPrice": 0
    }
  ]
}
```

`status` 허용값:
- `PENDING`
- `RELEASED`
- `SOLD_OUT`
- `HIDDEN`

Response data (`SellerProductResponse`):
```json
{
  "productId": 101,
  "sellerId": 11,
  "name": "Gray vintage 3D computer",
  "price": 50000,
  "category": "Outer",
  "description": "2026 spring collection",
  "status": "PENDING",
  "mainImageUrl": "https://cdn.example.com/products/main.jpg",
  "previewImageUrls": ["..."],
  "detailImageUrls": ["..."],
  "options": [
    {
      "optionId": 1001,
      "size": "Free",
      "color": "Black",
      "stockQuantity": 10,
      "additionalPrice": 0
    }
  ]
}
```

## 3.2 Seller 상품 목록
- `GET /product-service/api/v1/seller/products`

Query params:
- `status` (optional): `PENDING|RELEASED|SOLD_OUT|HIDDEN`
- `keyword` (optional): 상품명 부분검색
- `limit` (optional, default `50`, max `100`)

Response data: `SellerProductListResponse[]`
```json
[
  {
    "productId": 101,
    "name": "Gray vintage 3D computer",
    "price": 50000,
    "category": "Outer",
    "status": "PENDING",
    "mainImageUrl": "https://...",
    "sizes": ["Free", "S/M/L"],
    "colors": ["Black", "Navy"],
    "totalStock": 15
  }
]
```

## 3.3 Seller 상품 상세
- `GET /product-service/api/v1/seller/products/{productId}`
- Response: `SellerProductResponse`

## 3.4 Seller 상품 상태 변경
- `PATCH /product-service/api/v1/seller/products/{productId}/status`

Request:
```json
{
  "status": "RELEASED"
}
```

Response: 변경된 `SellerProductResponse`

## 3.5 Seller 상품 삭제
- `DELETE /product-service/api/v1/seller/products/{productId}`
- Response data: `null`

## 4. Product API (Buyer)

권한: `ROLE_BUYER` (현재 구현상 `ROLE_SELLER`도 조회 가능)

## 4.1 Buyer 상품 목록
- `GET /product-service/api/v1/buyer/products`

Query params:
- `category` (optional, exact match)
- `keyword` (optional, 상품명 부분검색)
- `sort` (optional, default `latest`)
  - `latest`
  - `priceAsc`
  - `priceDesc`
- `limit` (optional, default `20`, max `100`)

Response data: `BuyerProductListResponse[]`
```json
[
  {
    "productId": 101,
    "name": "Ripple Vase",
    "price": 100,
    "category": "Home",
    "mainImageUrl": "https://...",
    "liked": false
  }
]
```

## 4.2 Buyer 상품 상세
- `GET /product-service/api/v1/buyer/products/{productId}`
- `RELEASED` 상태 상품만 조회 가능

Response data: `BuyerProductDetailResponse`
```json
{
  "productId": 101,
  "name": "Ripple Vase",
  "price": 100,
  "category": "Home",
  "description": "...",
  "mainImageUrl": "https://...",
  "previewImageUrls": ["..."],
  "detailImageUrls": ["..."],
  "options": [
    {
      "optionId": 1001,
      "size": "180",
      "color": "Mint",
      "stockQuantity": 3,
      "additionalPrice": 0
    }
  ],
  "similarItems": [
    {
      "productId": 202,
      "name": "Porcelain Dinner Plate",
      "price": 48,
      "category": "Home",
      "mainImageUrl": "https://...",
      "liked": false
    }
  ]
}
```

## 5. 프론트 구현 체크리스트

1. 모든 요청에 `withCredentials: true`
2. 로그인/회원가입 성공 후 토큰은 JS에서 저장하지 말고 쿠키 기반으로만 사용
3. `403 FORBIDDEN_SELLER_ONLY` 수신 시 seller 페이지 접근 차단 화면 처리
4. buyer 페이지에서 미로그인 시 `401 ACCESS_TOKEN_MISSING` 처리 후 로그인 유도
5. 상품 등록 시 `options`는 최소 1개 필수

## 6. 자주 쓰는 FE 예시

```ts
// axios
const api = axios.create({
  baseURL: 'http://localhost:8000',
  withCredentials: true,
});

// buyer login
await api.post('/auth-service/api/v1/auth/buyers/login', {
  email,
  password,
});

// seller product create
await api.post('/product-service/api/v1/seller/products', payload);

// buyer product list
const res = await api.get('/product-service/api/v1/buyer/products', {
  params: { category: 'Home', sort: 'latest', limit: 20 },
});
```
