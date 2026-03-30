# OrderBook

Kotlin + Spring Boot 기반 실시간 주문 매칭 엔진 MSA 프로젝트입니다.

## Architecture

```
Client ──▶ Gateway ──▶ Order Service ──▶ Risk Service
                            │                  │
                            │   ◀── 검증 결과 ──┘
                            ▼
                      Matching Engine
                            │
                    ┌───────┼───────┐
                    ▼       ▼       ▼
                 Ledger   Query   Market Data
                Service  Service   Service
```

주문 접수 → Order Service가 Risk Service에 리스크 검증 요청(REST) → 승인 시 Order Service가 Matching Engine에 매칭 요청(REST) → 체결 이후 Kafka 이벤트로 원장, 조회 프로젝션, 시세 서비스에 비동기 전파됩니다.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| Framework | Spring Boot, Spring WebFlux |
| Messaging | Apache Kafka (KRaft) |
| Database | PostgreSQL (jOOQ, Flyway) |
| Cache | Redis |
| Observability | Micrometer, Prometheus, Grafana |
| Infrastructure | Docker Compose |
| Testing | JUnit 5, k6 |

## Modules

| Module | Description |
|--------|-------------|
| `services/gateway` | API 진입점, 정적 프론트엔드 |
| `services/order-service` | 주문 접수/취소/정정, transactional outbox |
| `services/risk-service` | 사전 리스크 평가, 이벤트 기반 잔고 추적 |
| `services/matching-engine` | 가격-시간 우선순위 매칭 엔진 |
| `services/ledger-service` | 현금/포지션 원장, 중복 체결 방지 |
| `services/query-service` | CQRS 조회 프로젝션 |
| `services/market-data-service` | 호가창/체결 조회, WebSocket 스트림 |
| `libs/common-events` | 공통 도메인 타입, 이벤트, API 계약 |
| `libs/common-kafka` | Kafka 공통 설정, 이벤트 직렬화 |
| `libs/common-observability` | 관측성 유틸리티 |

## Getting Started

### Docker Compose 전체 실행

```bash
docker compose -f infra/docker/docker-compose.yml up --build -d
```

### 인프라만 실행 후 로컬 개발

```bash
# Kafka, PostgreSQL, Redis, Prometheus, Grafana
docker compose -f infra/docker/docker-compose.yml up -d kafka postgres redis prometheus grafana

# 각 서비스 개별 실행
./gradlew :services:gateway:bootRun
./gradlew :services:order-service:bootRun
./gradlew :services:risk-service:bootRun
./gradlew :services:matching-engine:bootRun
./gradlew :services:ledger-service:bootRun
./gradlew :services:query-service:bootRun
./gradlew :services:market-data-service:bootRun
```

### 테스트

```bash
./gradlew test
```

## API

### 주문 생성

```bash
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{
    "accountId": "ACC-001",
    "clientOrderId": "sample-1",
    "symbol": "AAPL",
    "side": "BUY",
    "price": 100,
    "quantity": 10
  }'
```

### 조회

- `GET /orders/{orderId}` — 주문 상세
- `GET /accounts/{accountId}/balances` — 잔고
- `GET /accounts/{accountId}/positions` — 포지션
- `GET /market-data/{symbol}/orderbook` — 호가창
- `GET /market-data/{symbol}/trades` — 최근 체결

### WebSocket

- `/ws/market-data/orderbook/{symbol}` — 호가창 실시간 스트림
- `/ws/market-data/trades/{symbol}` — 체결 실시간 스트림

## Observability

- **Prometheus**: `http://localhost:9090`
- **Grafana**: `http://localhost:3000` (admin / admin)
- 각 서비스: `GET /actuator/prometheus`

## Ports

| Port | Service |
|------|---------|
| 8080 | gateway |
| 8081 | order-service |
| 8082 | risk-service |
| 8083 | matching-engine |
| 8084 | ledger-service |
| 8085 | query-service |
| 8086 | market-data-service |

## Known Limitations

- 인증/인가 미구현
- 지정가(Limit) 주문만 지원
- 외부 거래소/브로커 연동 없음
- 정산/청산 배치 미구현
