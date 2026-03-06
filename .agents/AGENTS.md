# Wearhouse Backend 코딩 컨벤션

이 문서는 Wearhouse 백엔드 프로젝트에서 사용하는 **코딩 규칙과 개발 컨벤션**을 정의한다.

프로젝트는 **Java + Spring Boot + MSA + Gradle Multi-module** 구조를 사용한다.

---

# 1. 패키지 구조

Wearhouse 프로젝트는 **도메인 중심 + infra 분리 패키지 구조**를 사용한다.

예시:

com.wearhouse.{module}
├ domain
│   └ {domain}
│       ├ controller
│       ├ dto
│       ├ service
│       │   ├ command
│       │   └ query
│       ├ event
│       ├ exception
│       ├ entity
│       └ model
├ infra
│   ├ jpa
│   │   ├ common
│   │   └ repository
│   └ kafka
│       ├ config
│       ├ consumer
│       ├ controller
│       ├ dto
│       └ service
└ support

원칙:

- 각 도메인은 `domain.{domain}` 하위에서 controller / service / entity / dto를 포함한다.
- JPA/Kafka 같은 구현 기술은 `infra` 패키지에 위치한다.
- 공통 유틸/헬퍼는 `support` 패키지에 위치한다.
- 서비스 계층은 CQRS 기준으로 `service/command`, `service/query` 하위 패키지로 분리한다.
- Kafka 연동 코드는 도메인 서비스와 분리하여 `infra/kafka` 패키지에 배치한다.

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

패키지 규칙:

- `domain.{domain}.service.command` : 상태 변경 로직(생성/수정/취소/상태전이)
- `domain.{domain}.service.query` : 조회 전용 로직

의존 규칙:

- Controller는 CommandService/QueryService를 목적에 맞게 분리 호출한다.
- Kafka Consumer는 `infra.kafka.consumer`에서 메시지를 수신하고, 실제 도메인 처리는 CommandService에 위임한다.
- Kafka 발행/재발행 로직은 `infra.kafka.service`에 위치시킨다.

---

# 4. DTO 규칙

DTO 이름 규칙:

객체 + 동작 + 타입

예시:

UserCreateRequest  
OrderCreateRequest  
ProductResponse

DTO 작성 규칙:

- DTO는 기본적으로 **record 사용**
- 요청/응답 DTO에서 불필요한 Builder 사용을 지양
- DTO는 데이터 전달에 집중하고, 비즈니스 변환/상태 변경 로직은 Service에서 처리

허용 메서드:

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
- ErrorException
- GlobalExceptionHandler

에러 응답 형식:

{
  "success": false,
  "code": "ORDER_NOT_FOUND",
  "message": "Order does not exist",
  "data": null,
  "timestamp": "2026-03-05T12:00:00"
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

# 13. 공통 응답 규칙

모든 API 응답은 `ApiResponse<T>` 포맷을 사용한다.

성공 응답 형식:

{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {},
  "timestamp": "2026-03-05T12:00:00"
}

실패 응답 형식:

{
  "success": false,
  "code": "COMMON_400_001",
  "message": "요청 값이 올바르지 않습니다.",
  "data": [],
  "timestamp": "2026-03-05T12:00:00"
}

원칙:

- Controller는 도메인 DTO를 반환하고, 공통 포맷 래핑은 `GlobalResponseBodyAdvice`가 담당한다.
- 비즈니스/도메인 오류는 `ErrorException` + `ErrorCode`로 처리한다.
- Validation 오류 상세는 `data` 필드에 배열/맵 형태로 담는다.
- 컨트롤러에서 수동으로 응답 포맷을 직접 만들지 않는다.

---

# 14. 환경변수(.env) 규칙

환경별 설정은 `.env` 기반으로 관리한다.

파일 규칙:

- `.env` : 로컬 개발자가 사용하는 실제 값 파일 (git 커밋 금지)
- `.env.example` : 공유용 템플릿 파일 (git 커밋 필수)
- 신규 환경변수 추가 시 `.env.example`를 반드시 함께 업데이트한다.

보안 규칙:

- 비밀번호, API Key, 토큰, 인증서 값은 `.env`에만 저장한다.
- 실제 시크릿을 문서, 코드, 커밋 메시지에 남기지 않는다.
- 운영/스테이징 시크릿은 AWS Parameter Store/Secrets Manager 등 외부 시크릿 저장소를 사용한다.

사용 규칙:

- Spring 설정(`application-*.yml`)에서는 `${ENV_NAME}` 형태로 참조한다.
- 기본값이 필요한 값은 `${ENV_NAME:default}` 형식을 사용한다.
- 로컬은 `application-local.yml` + `.env` 조합을 기본으로 한다.
- 프로덕션은 `.env` 파일 직접 배포 대신 CI/CD 주입 방식을 사용한다.

검증 규칙:

- 애플리케이션 기동 시 필수 환경변수 누락 여부를 검사한다.
- `.env.example`은 최소 실행 가능한 값으로 유지한다.

---

# 15. Kafka 패키지 분리 규칙

Kafka 관련 구성은 아래처럼 분리한다.

- `infra.kafka.config` : Topic/Producer/Consumer 공통 설정
- `infra.kafka.consumer` : Kafka Listener
- `infra.kafka.service` : Kafka publish, outbox publish 보조 서비스
- `infra.kafka.controller` : 내부 테스트/운영 점검용 엔드포인트
- `infra.kafka.dto` : Kafka 테스트/관리 API DTO

원칙:

- 도메인 비즈니스 규칙(주문 상태 전이/검증)은 `domain` 패키지에서 관리한다.
- Kafka I/O, 직렬화/역직렬화, 토픽 전송은 `infra.kafka`에서 관리한다.

End of skill guide.
