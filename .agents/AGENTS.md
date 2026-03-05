# Wearhouse Backend 코딩 컨벤션

이 문서는 Wearhouse 백엔드 프로젝트에서 사용하는 **코딩 규칙과 개발 컨벤션**을 정의한다.

프로젝트는 **Java + Spring Boot + MSA + Gradle Multi-module** 구조를 사용한다.

---

# 1. 패키지 구조

Wearhouse 프로젝트는 **도메인 중심 패키지 구조**를 사용한다.

예시:

com.wearhouse

domain
├ user
│   ├ controller
│   ├ dto
│   ├ entity
│   ├ exception
│   ├ repository
│   ├ service
│
├ product
│   ├ controller
│   ├ dto
│   ├ entity
│   ├ exception
│   ├ repository
│   ├ service
│
global
├ auth
├ config
├ error
├ common
├ infra
└ util

원칙:

- 각 도메인은 controller / service / repository / entity / dto를 포함한다.
- 공통 로직은 global 패키지에 위치한다.

---

# 2. Service Layer 규칙

ServiceImpl 패턴을 사용하지 않는다.

권장:

UserService  
OrderService  
ProductService

지양:

UserService  
UserServiceImpl

이유:

- 코드 단순화
- 불필요한 인터페이스 제거
- 가독성 향상

---

# 3. CQRS 패턴

Command와 Query 로직을 분리한다.

예시:

UserCommandService  
UserQueryService

Command:

- 생성
- 수정
- 삭제

Query:

- 조회
- 검색

---

# 4. DTO 규칙

DTO 이름 규칙:

객체 + 동작 + 타입

예시:

UserCreateRequest  
OrderCreateRequest  
ProductResponse

DTO 작성 규칙:

- record 대신 **class 사용**
- **Builder 패턴 사용**
- DTO 내부에 변환 로직 허용

허용 메서드:

toEntity()  
from(Entity)  
of(Entity, extra)

---

# 5. 네이밍 규칙

클래스 이름:

명사 + 명사

예시:

OrderService  
UserValidator  
ProductRepository

메서드 이름:

동사 + 명사

예시:

createOrder()  
findUser()  
updateInventory()

---

# 6. Lombok 규칙

허용:

@Getter  
@Builder  
@RequiredArgsConstructor

금지:

@Setter

Entity 상태 변경은 반드시 도메인 메서드를 통해 수행한다.

예시:

order.confirmPayment()  
inventory.reserveStock()

---

# 7. Entity 수정 정책

여러 필드를 변경할 경우 setter 대신 도메인 메서드를 사용한다.

예시:

updateProfile(UpdateProfileCommand command)

---

# 8. Validation

Bean Validation을 사용한다.

예시:

@NotBlank  
@Email  
@Size

Controller에서는 반드시 다음을 사용한다.

@Valid

---

# 9. 에러 처리

Global Error Handler를 사용한다.

구조:

global/error

- ErrorCode
- BusinessException
- GlobalExceptionHandler

응답 형식:

{
"code": "ORDER_NOT_FOUND",
"message": "Order does not exist"
}

---

# 10. Swagger 문서화

다음 어노테이션을 사용한다.

@Tag  
@Operation  
@Schema

---

# 11. 로깅

System.out 사용 금지.

다음 사용:

log.info()  
log.warn()  
log.error()

---

# 12. Monorepo 규칙

프로젝트는 **Gradle Multi-module 구조**를 사용한다.

모듈 목록:

- api-gateway-service
- auth-service
- user-service
- product-service
- order-service
- payment-service
- inventory-service
- settlement-service
- common

각 모듈은 독립 실행 가능해야 한다.

---

End of skill guide.