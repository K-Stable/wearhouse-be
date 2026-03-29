# 서비스 디렉토리/네이밍 컨벤션

## 기준

| 구분 | 규칙 | 예시 |
|---|---|---|
| Controller | `Actor + Context + Controller` | `BuyerProductController`, `SellerUserController` |
| Service | `Actor + Context + Action + Service` 또는 `Context + Internal + Action + Service` | `BuyerUserCommandService`, `PaymentInternalCommandService` |
| Internal 패키지 | `.../internal/controller`, `.../internal/service` 분리 | `com.wearhouse.user.internal.*` |
| Actor 패키지 | `.../buyer/*`, `.../seller/*` 분리 | `com.wearhouse.product.buyer.controller` |

## 이번 정리 반영

| 모듈 | 변경 내용 |
|---|---|
| `user` | `Buyer/Seller/Internal`을 actor/internal 패키지로 분리, `*User*` 네이밍 통일 |
| `product` | `ProductBuyer/ProductSeller` → `BuyerProduct/SellerProduct`, internal controller 분리 |
| `payment` | `PaymentCommandService` → `PaymentInternalCommandService`로 명확화 |

