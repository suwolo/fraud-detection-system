# Fraud Detection System

Two Spring Boot (Java 21) services backed by PostgreSQL, Kafka, Redis and Elasticsearch. Clients submit payment transactions to the **transaction-service**, which queues them on Kafka. The **fraud-service** scores each one against configurable rules and publishes the decision. The transaction-service stores the decision, indexes it for search, and exposes query, search and reporting endpoints.

## Architecture

```
client --HTTP--> transaction-service (:8080) --[transaction.submitted]--> Kafka --> fraud-service (:8081)
                   |  ^                                                              |        |
                   |  +-------- consumers and Kafka Streams <--[transaction.scored]--+        +--> Redis
                   |                                                                              (velocity window)
                   +--JDBC--------> PostgreSQL      system of record
                   +--------------> Redis           response cache
                   +--------------> Elasticsearch   search index
```

| Component | Port | Role |
|---|---|---|
| transaction-service | 8080 | Public API: submit, get and list transactions, search, account statistics, reports. Owns the database. Publishes submitted transactions and consumes decisions. |
| fraud-service | 8081 | Scoring engine. Consumes submitted transactions, runs the enabled rules and publishes the decision. `POST /score` stays available for direct, synchronous scoring. |
| postgres | none published | Stores `accounts` and `transactions`. Schema and seed data come from `db/init/01-init.sql`. |
| kafka | none published | Single-node broker in KRaft mode (no Zookeeper). Carries the topics below. |
| redis | none published | Response cache for transaction-service and the sliding velocity window for fraud-service. |
| elasticsearch | none published | Search index of scored transactions. Single node, security disabled, for local use. |

### Kafka topics

| Topic | Key | Value (JSON) | Producer | Consumers |
|---|---|---|---|---|
| `transaction.submitted` | account id | scoring request: `transaction`, `recentTransactions`, `previousTransaction` | transaction-service | fraud-service (group `fraud-service`) |
| `transaction.scored` | account id | `transaction` and `decision` | fraud-service | transaction-service: group `transaction-service-decision` (stores the decision), group `transaction-service-indexer` (indexes into Elasticsearch), and the Kafka Streams application |

Each topic has 3 partitions. The key is the account id, so one account's messages stay in order. A message that keeps failing is retried 3 times, one second apart, then published to `<topic>.DLT`. Malformed or invalid messages go to the dead-letter topic without retries. Delivery is at-least-once, and every consumer is idempotent.

### Submit flow (asynchronous)

1. `POST /api/v1/transactions` validates the request and checks the account (cached in Redis).
2. transaction-service inserts the row with status `RECEIVED`, loads the account's recent transactions (within `fraud.history-window-minutes`) and its previous transaction, publishes them to `transaction.submitted`, and returns **202 Accepted** with status `RECEIVED`.
3. fraud-service scores the message and publishes the decision to `transaction.scored`.
4. transaction-service updates the row to `APPROVED` or `FLAGGED` with the risk score and per-rule results, and drops the cached copy. The indexer consumer writes the transaction to Elasticsearch.
5. The client reads the decision with `GET /api/v1/transactions/{id}`. The status stays `RECEIVED` until the decision is stored.

If the message cannot be published, the request returns 503 and the row stays `RECEIVED`. Nothing re-publishes such rows automatically.

Scoring rules (fraud-service): `HighAmountRule`, `VelocityRule`, `CountryMismatchRule`, `UnusualHourRule`. Each fired rule adds its weight; the score is the sum, capped at 100, and the transaction is flagged when the score reaches `fraud.flag-threshold`.

### Redis

**Response cache (transaction-service).** Cache-aside with a TTL per entry. If Redis is unavailable, requests read from PostgreSQL instead.

| Key | Content | TTL | Invalidation |
|---|---|---|---|
| `account:{id}` | account exists (only a positive answer is cached) | 1 hour | TTL |
| `txn:{id}` | transaction response, only once it is `APPROVED` or `FLAGGED` | 10 minutes | evicted when the decision is stored |
| `stats:{account}:{from}:{to}` | account statistics | 2 minutes | TTL, so new transactions can take up to 2 minutes to appear |
| `report:flagged-accounts:{n}`, `report:risky-merchants:{from}:{to}` | report results | 1 minute | TTL |

**Velocity window (fraud-service).** With `app.velocity.store=redis` (the default), `VelocityRule` counts an account's transactions in a Redis sorted set `velocity:{accountId}` (member = transaction id, score = event time). Adding the same transaction twice changes nothing, so redelivered messages are not double counted. Entries older than 24 hours are trimmed and the key expires after 24 hours. With `request`, or if Redis fails, the rule counts from the recent transactions sent in the message.

### Elasticsearch

Scored transactions are indexed in the `transactions` index, which is created on first use. PostgreSQL stays the system of record. The seeded transactions never pass through Kafka, so call `POST /api/v1/search/reindex` once to load them. The same call rebuilds the index from the database at any time.

### Kafka Streams

transaction-service runs a Kafka Streams topology over `transaction.scored`. It counts flagged transactions per merchant in 5-minute tumbling windows (`app.streams.window-minutes`), kept in a local state store that `GET /api/v1/reports/live-risky-merchants` reads. Windows use the time the decision was produced, not the payment's `eventTime`. The store holds only the partitions assigned to the running instance.

## Running

Prerequisites: Docker with Compose v2 and about 3 GB of free memory (Elasticsearch alone is configured for a 512 MB heap).

```bash
cp .env.example .env
docker compose up --build
```

Startup order is enforced by health checks: postgres, kafka, redis and elasticsearch first, then fraud-service, then transaction-service. When all are healthy:

- transaction-service: http://localhost:8080
- fraud-service: http://localhost:8081

Health checks: `curl http://localhost:8080/actuator/health` and `curl http://localhost:8081/actuator/health`. Health includes Redis (both services) and Elasticsearch (transaction-service), so a service reports `DOWN` while one of them is unreachable.

After the first start, load the seeded transactions into the search index:

```bash
curl -X POST http://localhost:8080/api/v1/search/reindex
```

The database init script runs only on an empty volume. To reset all data (PostgreSQL, Kafka, Redis, Elasticsearch) and re-seed: `docker compose down -v`.

## Configuration

### Environment variables (`.env`, read by `docker-compose.yml`)

| Variable | Used by | Meaning | Default when run outside Docker |
|---|---|---|---|
| `DB_HOST` | transaction-service | Database host | `localhost` |
| `DB_PORT` | transaction-service | Database port | `5432` |
| `DB_NAME` | postgres, transaction-service | Database name | `frauddb` |
| `DB_USER` | postgres, transaction-service | Database user | `fraud` |
| `DB_PASSWORD` | postgres, transaction-service | Database password | `fraud` |
| `KAFKA_BOOTSTRAP_SERVERS` | both services | Kafka bootstrap servers | `localhost:9092` |
| `REDIS_HOST` | both services | Redis host | `localhost` |
| `REDIS_PORT` | both services | Redis port | `6379` |
| `ELASTICSEARCH_URIS` | transaction-service | Elasticsearch URL | `http://localhost:9200` |
| `VELOCITY_STORE` | fraud-service | `redis` or `request` (see Redis above) | `redis` |

`.env.example` lists the values to use with Compose (hosts `postgres`, `kafka`, `redis`, `elasticsearch`). The Kafka, Redis, Elasticsearch and velocity variables have defaults in `docker-compose.yml`, so an older `.env` without them still works. `FRAUD_SERVICE_URL` is no longer used. Do not commit `.env`.

### transaction-service properties (`transaction-service/src/main/resources/application.yml`)

| Property | Default | Meaning |
|---|---|---|
| `fraud.history-window-minutes` | `10` | How far back recent transactions are loaded and sent with each message |
| `app.kafka.submitted-topic` | `transaction.submitted` | Topic for transactions waiting to be scored |
| `app.kafka.scored-topic` | `transaction.scored` | Topic for decisions |
| `app.kafka.partitions` | `3` | Partitions of the topics it creates |
| `app.kafka.send-timeout-ms` | `5000` | How long submit waits for the broker acknowledgement |
| `app.kafka.retry-attempts` | `3` | Retries before a message goes to the dead-letter topic |
| `app.kafka.retry-interval-ms` | `1000` | Delay between retries |
| `app.streams.window-minutes` | `5` | Window size of the flagged-per-merchant count |
| `spring.kafka.streams.application-id` | `transaction-service-streams` | Kafka Streams application id |

### fraud-service properties

Kafka and velocity settings (`application.yml`):

| Property | Default | Meaning |
|---|---|---|
| `app.kafka.submitted-topic` | `transaction.submitted` | Topic it consumes |
| `app.kafka.scored-topic` | `transaction.scored` | Topic it publishes to |
| `app.kafka.retry-attempts`, `app.kafka.retry-interval-ms` | `3`, `1000` | Retry policy before the dead-letter topic |
| `app.velocity.store` | `redis` | `redis` or `request` |

Scoring rules (`fraud.*`, bound by `FraudProperties`). Defaults are defined in code. Override them in `fraud-service/src/main/resources/application.yml`, or as environment variables on the `fraud-service` container using Spring relaxed binding, for example `FRAUD_RULES_HIGHAMOUNT_THRESHOLD=20000000`. `docker-compose.yml` does not forward these variables by default, so add them under the `fraud-service` `environment:` key.

| Property | Default | Constraint |
|---|---|---|
| `fraud.flag-threshold` | `60` | 0 to 100 |
| `fraud.rules.high-amount.enabled` | `true` | |
| `fraud.rules.high-amount.weight` | `40` | 0 to 100 |
| `fraud.rules.high-amount.threshold` | `10000000` | amount at or above this fires the rule |
| `fraud.rules.velocity.enabled` | `true` | |
| `fraud.rules.velocity.weight` | `30` | 0 to 100 |
| `fraud.rules.velocity.max-count` | `5` | at least 1 |
| `fraud.rules.velocity.window-minutes` | `10` | at least 1 |
| `fraud.rules.country-mismatch.enabled` | `true` | |
| `fraud.rules.country-mismatch.weight` | `50` | 0 to 100 |
| `fraud.rules.country-mismatch.min-travel-minutes` | `120` | 1 to 1440 |
| `fraud.rules.unusual-hour.enabled` | `true` | |
| `fraud.rules.unusual-hour.weight` | `25` | 0 to 100 |
| `fraud.rules.unusual-hour.start-hour` | `0` | 0 to 24 (UTC) |
| `fraud.rules.unusual-hour.end-hour` | `5` | 0 to 24 (UTC) |
| `fraud.rules.unusual-hour.min-amount` | `5000000` | |

Invalid values (out of range, or rule weights rejected by `RuleWeightValidator`) stop fraud-service at startup. With the Redis velocity store, `fraud.rules.velocity.window-minutes` is the window that is counted. With `request`, the window is `fraud.history-window-minutes` in transaction-service.

## Seeded data

`db/init/01-init.sql` creates the schema and seeds six accounts plus 56 transactions (6 hand-written rule examples, 30 generated approved, 20 generated flagged).

| Account | Home country |
|---|---|
| `acc-001` | ID |
| `acc-002` | ID |
| `acc-003` | SG |
| `acc-004` | MY |
| `acc-005` | US |
| `acc-006` | GB |

Seeded transactions are dated 2024-05-01 to 2024-06-16 (UTC) and have ids starting `tx-seed-` (for example `tx-seed-v005`, a flagged MY to US transaction on `acc-004`). Submitted transactions must reference an existing account.

## API

Base URL for the examples: `http://localhost:8080`. Timestamps are ISO-8601 UTC instants. Invalid input returns a 4xx error body; an unknown id returns 404.

### Submit a transaction: `POST /api/v1/transactions`

Returns `202 Accepted`. The transaction is stored and queued for scoring, so the response has status `RECEIVED` and no `riskScore` or `ruleResults`. Read the decision with `GET /api/v1/transactions/{id}`, usually available within a second or two. Returns 503 if the transaction cannot be queued.

Required fields: `accountId`, `amount` (0.01 to 999999999.99, at most 2 decimals), `currency` (three uppercase letters), `merchantId`, `merchantCategory`, `country`, `eventTime`.

```bash
curl -i -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "accountId": "acc-001",
    "amount": 25000000.00,
    "currency": "IDR",
    "merchantId": "mrc-electro",
    "merchantCategory": "ELECTRONICS",
    "country": "ID",
    "eventTime": "2024-07-01T03:30:00Z"
  }'
```

The response contains `id`, the submitted fields and `status`.

### Get a transaction: `GET /api/v1/transactions/{id}`

Once scored, the response also has `riskScore` (0 to 100) and `ruleResults`, and `status` is `APPROVED` or `FLAGGED`.

```bash
curl http://localhost:8080/api/v1/transactions/tx-seed-v005
```

### List transactions: `GET /api/v1/transactions`

Optional query parameters: `accountId`, `status` (`RECEIVED`, `APPROVED` or `FLAGGED`), `page` (default 0), `size` (default 20, 1 to 100).

```bash
# first page, default size
curl "http://localhost:8080/api/v1/transactions"

# flagged transactions for one account, 10 per page
curl "http://localhost:8080/api/v1/transactions?accountId=acc-004&status=FLAGGED&page=0&size=10"
```

### Search transactions: `GET /api/v1/search/transactions`

Searches the Elasticsearch index of scored transactions, newest first. All parameters are optional:

| Parameter | Meaning |
|---|---|
| `q` | Full text over merchant id, merchant category and the explanations of the fired rules |
| `accountId`, `merchantId`, `country` | Exact match |
| `status` | `APPROVED` or `FLAGGED` (`RECEIVED` rows are not indexed) |
| `rule` | Name of a fired rule, for example `HighAmountRule` |
| `minAmount`, `maxAmount` | Amount range, inclusive |
| `page`, `size` | Paging, default 0 and 20, size 1 to 100 |

```bash
curl "http://localhost:8080/api/v1/search/transactions?status=FLAGGED&rule=HighAmountRule&minAmount=10000000"
```

Each hit has the transaction fields, `status`, `riskScore` and `firedRules` (names only, weights are not indexed).

### Rebuild the search index: `POST /api/v1/search/reindex`

Reads all scored transactions from PostgreSQL into the index and returns `{"indexed": <count>}`.

```bash
curl -X POST http://localhost:8080/api/v1/search/reindex
```

### Account statistics: `GET /api/v1/accounts/{id}/statistics`

Required: `from` and `to` (ISO-8601 instants, `from` not after `to`). Returns 404 for an unknown account. Cached for 2 minutes.

```bash
curl "http://localhost:8080/api/v1/accounts/acc-001/statistics?from=2024-05-01T00:00:00Z&to=2024-07-01T00:00:00Z"
```

### Report: flagged accounts: `GET /api/v1/reports/flagged-accounts`

Required: `minCount` (positive integer). Lists accounts with more than that many flagged transactions. Cached for 1 minute.

```bash
curl "http://localhost:8080/api/v1/reports/flagged-accounts?minCount=3"
```

### Report: risky merchants: `GET /api/v1/reports/risky-merchants`

Required: `from` and `to` (ISO dates `yyyy-MM-dd`, `from` not after `to`, range at most 366 days). Cached for 1 minute.

```bash
curl "http://localhost:8080/api/v1/reports/risky-merchants?from=2024-05-01&to=2024-06-30"
```

### Report: live risky merchants: `GET /api/v1/reports/live-risky-merchants`

Top 10 merchants by flagged transactions in the windows that overlap the last `minutes` minutes (default 5, 1 to 60), from Kafka Streams. Returns 503 while the stream application is starting. Only transactions scored since the service started are counted.

```bash
curl "http://localhost:8080/api/v1/reports/live-risky-merchants?minutes=15"
```

### Score directly (fraud-service): `POST http://localhost:8081/score`

Synchronous and stateless. The caller supplies the history. `transaction` is required; `recentTransactions` (list, defaults to empty) and `previousTransaction` (`eventTime` and `country`) are optional. Every field of a transaction is required. This endpoint does not publish to Kafka. With the Redis velocity store, `recentTransactions` is ignored for `VelocityRule`, and the transaction is recorded in the account's Redis window.

```bash
curl -X POST http://localhost:8081/score \
  -H "Content-Type: application/json" \
  -d '{
    "transaction": {
      "id": "tx-demo-1",
      "accountId": "acc-004",
      "amount": 450000.00,
      "currency": "MYR",
      "merchantId": "mrc-travel",
      "merchantCategory": "TRAVEL",
      "country": "US",
      "eventTime": "2024-07-01T10:00:00Z"
    },
    "recentTransactions": [],
    "previousTransaction": { "eventTime": "2024-07-01T09:30:00Z", "country": "MY" }
  }'
```

The response is a decision with the risk score, the flag decision and the per-rule results.

## Postman

Import `postman/fraud-detection-system.postman_collection.json` into Postman. It covers every endpoint of both services, plus error cases, with status-code tests. Collection variables: `baseUrl` (http://localhost:8080), `fraudUrl` (http://localhost:8081), `accountId` (acc-001) and `transactionId` (set automatically by the submit requests). Run "Reindex Elasticsearch from PostgreSQL" before the search requests.

## Tests

Requires JDK 21 and Maven. From the repository root:

```bash
mvn test
```

To test one module: `mvn test -pl fraud-service` or `mvn test -pl transaction-service`. Tests include unit tests and property-style parameterized tests. They use mocks and need no running Kafka, Redis, Elasticsearch or database. The Kafka, Redis and Elasticsearch integration is exercised only by running the Compose stack.

## Project layout

```
fraud-service/          scoring service
  src/main/java/com/example/fraud/
    config/             FraudProperties, RuleWeightValidator, KafkaConfig (topics, dead-letter handling)
    controller/         ScoreController (POST /score)
    dto/                request, response and event records
    exception/          GlobalExceptionHandler
    messaging/          ScoringListener (consumes submitted, publishes scored)
    rule/               FraudRule, the four rule beans, VelocityCounter (Redis or request history)
    service/            ScoringService
transaction-service/    public API, persistence, search, reports
  src/main/java/com/example/txn/
    cache/              CacheService (Redis cache-aside)
    controller/         transaction, search, statistics and report endpoints
    dto/                request, response, event and internal records
    exception/          domain exceptions and GlobalExceptionHandler
    messaging/          KafkaConfig, TransactionEventPublisher, ScoredEventListener
    repository/         TransactionRepository (native SQL)
    search/             Elasticsearch document, search service
    service/            TransactionService, StatisticsService, ReportService
    streams/            Kafka Streams topology and its query service
db/init/01-init.sql     schema and seed data
postman/                Postman collection
docker-compose.yml      postgres, kafka, redis, elasticsearch and both services
.env.example            template for .env
```
