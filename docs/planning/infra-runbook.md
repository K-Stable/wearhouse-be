# Wearhouse 인프라 실행/검증 런북

이 문서는 현재 프로젝트에 반영된 인프라 구성, 실행 방법, 검증 방법을 한 곳에 정리한 기준 문서입니다.

## 1. 현재 반영된 구성

- MySQL (`wearhouse-mysql`)
- Redis (`wearhouse-redis`)
- Zookeeper (`wearhouse-zookeeper`)
- Kafka (`wearhouse-kafka`)
- Kafka UI (`wearhouse-kafka-ui`)
- Kafka Exporter (`wearhouse-kafka-exporter`)
- Prometheus (`wearhouse-prometheus`)
- Grafana (`wearhouse-grafana`)
- Alertmanager (`wearhouse-alertmanager`)
- Eureka Service Discovery (`wearhouse-service-discovery`)
- Spring Cloud Config Server (`wearhouse-config-server`)
- Order Service (`wearhouse-order-service`)
- API Gateway (`wearhouse-api-gateway`)

## 2. 사전 준비

- Docker Desktop 실행
- 프로젝트 루트의 `.env` 값 확인
- 주요 환경변수:
  - MySQL: `MYSQL_ROOT_PASSWORD`, `MYSQL_USER`, `MYSQL_PASSWORD`
  - DB 이름: `AUTH_DB_NAME`, `USER_DB_NAME`, `PRODUCT_DB_NAME`, `ORDER_DB_NAME`, `PAYMENT_DB_NAME`, `INVENTORY_DB_NAME`, `SETTLEMENT_DB_NAME`
  - Redis: `REDIS_HOST`, `REDIS_PORT`
  - Config: `SPRING_CLOUD_CONFIG_URI`, `CONFIG_SERVER_PORT`, `CONFIG_REPO_LOCATIONS`
  - Eureka: `EUREKA_SERVER_PORT`, `EUREKA_DEFAULT_ZONE`
  - Kafka: `KAFKA_PORT`, `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_TEST_TOPIC`

## 3. 실행 방법

전체 실행:

```bash
docker compose up -d --build
```

상태 확인:

```bash
docker compose ps
```

전체 로그(최근 200줄):

```bash
docker compose logs --tail=200
```

종료:

```bash
docker compose down
```

볼륨까지 초기화:

```bash
docker compose down -v
```

## 4. 기능별 검증 방법

### 4.1 Eureka

- URL: `http://localhost:8761`
- 확인 포인트: `order-service`, `api-gateway` 인스턴스 등록 여부

### 4.2 Spring Cloud Config

설정 조회:

```bash
curl http://localhost:8888/order-service/default
```

확인 포인트:

- JSON 응답이 오고 `propertySources`에 설정이 포함되는지

### 4.3 Redis

```bash
docker exec -it wearhouse-redis redis-cli ping
```

확인 포인트:

- `PONG` 반환

### 4.4 MySQL

DB 목록:

```bash
docker exec -it wearhouse-mysql sh -lc 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SHOW DATABASES;"'
```

Flyway 이력(order 예시):

```bash
docker exec -it wearhouse-mysql sh -lc 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -D wearhouse_order -e "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;"'
```

베이스라인 테이블(order 예시):

```bash
docker exec -it wearhouse-mysql sh -lc 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -D wearhouse_order -e "SELECT * FROM schema_baseline;"'
```

### 4.5 Kafka

토픽 목록:

```bash
docker exec -it wearhouse-kafka kafka-topics --bootstrap-server kafka:29092 --list
```

테스트 메시지 발행(API Gateway 경유):

```bash
curl -X POST "http://localhost:8000/order-service/api/v1/internal/kafka/test-publish?message=hello-kafka"
```

메시지 컨슘 확인:

```bash
docker exec -it wearhouse-kafka kafka-console-consumer \
  --bootstrap-server kafka:29092 \
  --topic wearhouse.test.topic \
  --from-beginning \
  --max-messages 1
```

### 4.6 모니터링

- Prometheus: `http://localhost:9090`
  - Targets: `http://localhost:9090/targets`
- Grafana: `http://localhost:3000`
  - 기본 계정: `.env`의 `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD`
- Kafka UI: `http://localhost:8085`
- Alertmanager: `http://localhost:9093`

### 4.7 프론트 연동(local)

- 공통 API Gateway 주소: `http://localhost:8000`
- buyer 프론트(`wearhouse-fe`) 권장 실행:
  - `npm run dev:local` (127.0.0.1:3001)
- seller 프론트(`wearhouse-seller-fe`) 기본 실행:
  - `npm run dev` (localhost:3000)
- CORS 허용 Origin은 `API_GATEWAY_ALLOWED_ORIGINS`로 제어한다.
  - 기본값: `http://localhost:3000,http://127.0.0.1:3000,http://localhost:3001,http://127.0.0.1:3001`
- 프론트 API baseURL 권장:
  - `NEXT_PUBLIC_API_BASE_URL=http://localhost:8000`
- 라우팅 규칙:
  - 주문 API: `${NEXT_PUBLIC_API_BASE_URL}/order-service/api/v1/orders`
  - 재고 API: `${NEXT_PUBLIC_API_BASE_URL}/inventory-service/api/v1/internal/inventory`

### 4.8 인증/로그인(local)

- Gateway 경유 URL:
  - Buyer 회원가입: `POST http://localhost:8000/user-service/api/v1/users/buyers/signup`
  - Buyer 로그인: `POST http://localhost:8000/auth-service/api/v1/auth/buyers/login`
  - Seller 회원가입: `POST http://localhost:8000/user-service/api/v1/users/sellers/signup`
  - Seller 로그인: `POST http://localhost:8000/auth-service/api/v1/auth/sellers/login`
- 예시(Buyer 로그인):

```bash
curl -i -X POST "http://localhost:8000/auth-service/api/v1/auth/buyers/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"buyer@example.com","password":"pass1234"}'
```

- 응답의 `Set-Cookie`로 `buyer_access_token`, `buyer_refresh_token`이 내려오면 성공
- 이후 API 호출 시 동일 쿠키를 포함해 Gateway 경유 호출
- 로그아웃(`.../logout`) 시 Auth가 `AUTH_USER_CHANGED_CHANNEL`로 사용자 변경 이벤트를 발행하고,
  Gateway는 해당 사용자 Passport 캐시를 Pub/Sub 기반으로 즉시 무효화한다.

## 5. 트러블슈팅

- 이미지 pull 실패(`ImagePullBackOff`, `no match for platform in manifest`) 상세 대응:
  - `image-platform-mismatch-troubleshooting.md` 참고

MySQL 컨테이너가 바로 종료될 때:

- 원인: `MYSQL_USER=root` 설정
- 조치: `MYSQL_USER`는 일반 계정명(예: `wearhouse`)을 사용하고 root는 `MYSQL_ROOT_PASSWORD`만 사용

`Access denied for user 'root'@'localhost'`:

- 원인: 호스트 쉘 변수 확장 불일치 가능
- 조치: `docker exec ... sh -lc '... -p"$MYSQL_ROOT_PASSWORD" ...'` 형식으로 컨테이너 내부 환경변수를 사용

Config 조회 실패:

- `wearhouse-config-server` 로그 확인
- `config-repo` 마운트 및 `CONFIG_REPO_LOCATIONS` 값 확인

Kafka 발행은 되는데 모니터링 그래프가 안 보일 때:

- `kafka-exporter`가 올바른 브로커(`kafka:29092`)를 바라보는지 확인
- Prometheus `targets` 페이지에서 `kafka-exporter` 상태 확인

## 6. 문서 업데이트 규칙

- 인프라 구성 변경 시 이 문서의 `1. 현재 반영된 구성`과 `4. 기능별 검증 방법`을 함께 수정
- 명령어 변경 시 실행 결과 기준으로 즉시 갱신
- 운영 이슈 해결 방법은 `5. 트러블슈팅`에 누적
