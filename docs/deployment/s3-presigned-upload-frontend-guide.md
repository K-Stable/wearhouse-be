# Product Image Upload Guide (Presigned URL)

## Summary
- Upload type: browser direct upload to S3 using presigned URL
- API base: `https://api.wear-house.shop`
- Presigned endpoint: `POST /api/v1/seller/products/images/presigned-upload`
- Auth: seller access token (`Authorization: Bearer <token>`)

## Storage Strategy
- We store product images by type in separate folders.
- Folder policy:
  - main image: `.../main/...`
  - preview images: `.../previews/...`
  - detail images: `.../details/...`
- Full key format:
  - `{prefix}/products/seller-{sellerId}/{typeFolder}/{yyyy}/{MM}/{dd}/{uuid}.{ext}`
  - Example:
    - `dev/products/seller-12/main/2026/03/26/550e8400-e29b-41d4-a716-446655440000.png`

## Step 1) Request Presigned URL
Request:

```http
POST /api/v1/seller/products/images/presigned-upload
Authorization: Bearer <SELLER_ACCESS_TOKEN>
Content-Type: application/json
```

```json
{
  "fileName": "main-image.png",
  "contentType": "image/png",
  "imageType": "MAIN"
}
```

Response:

```json
{
  "success": true,
  "code": "PRODUCT_201_003",
  "message": "상품 이미지 업로드 URL 생성에 성공했습니다.",
  "data": {
    "imageKey": "dev/products/seller-12/main/2026/03/26/uuid.png",
    "uploadUrl": "https://wearhouse-231629457117.s3.ap-northeast-2.amazonaws.com/...",
    "imageUrl": "https://wearhouse-231629457117.s3.ap-northeast-2.amazonaws.com/dev/products/seller-12/main/2026/03/26/uuid.png"
  },
  "timestamp": "2026-03-26T09:00:00"
}
```

## Step 2) Upload File to S3
Use the returned `uploadUrl` and upload binary file with `PUT`.

```ts
await fetch(uploadUrl, {
  method: "PUT",
  headers: { "Content-Type": file.type },
  body: file
});
```

## Step 3) Create Product with Uploaded Image Keys
- In product create request, send `imageKey` values.
- Recommended mapping:
  - `mainImageUrl` <- main image `imageKey`
  - `previewImageUrls` <- preview image `imageKey[]`
  - `detailImageUrls` <- detail image `imageKey[]`

```json
{
  "name": "Example Product",
  "price": 39000,
  "category": "TOP",
  "options": [
    { "size": "M", "color": "BLACK", "stockQuantity": 10 }
  ],
  "mainImageUrl": "dev/products/seller-12/main/2026/03/26/uuid-main.png",
  "previewImageUrls": [
    "dev/products/seller-12/previews/2026/03/26/uuid-preview-1.png"
  ],
  "detailImageUrls": [
    "dev/products/seller-12/details/2026/03/26/uuid-detail-1.png"
  ],
  "status": "PENDING"
}
```

## Notes
- `contentType` must start with `image/` (e.g. `image/png`, `image/jpeg`, `image/webp`).
- Presigned URL expiration is controlled by `S3_PRESIGNED_PUT_EXPIRE_SECONDS`.
- Browser upload requires S3 CORS configuration (already set for production domains).
