# Savings Account API

A small Spring Boot service for opening and fetching savings bank accounts.

- Java 17, Spring Boot 4.1, Maven
- PostgreSQL (schema managed by Flyway)
- Redis for caching `GET` requests
- Tests use Testcontainers, so they run against real Postgres and Redis

## Running it

You need **Java 17+** and **Docker**. Maven is not required, the wrapper (`./mvnw`) downloads it.

```bash
./mvnw spring-boot:run
```

This starts Postgres and Redis from `compose.yaml` automatically (via Spring Boot's Docker Compose
support), runs the Flyway migration and starts the API on http://localhost:8080. The containers are
stopped again when the app shuts down.

If you'd rather run the containers yourself, or point at an existing database:

```bash
docker compose up -d
./mvnw package
java -jar target/savings-account-api-0.0.1-SNAPSHOT.jar
```

Connection settings can be overridden with `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST` and `REDIS_PORT`.

> Note: `compose.yaml` binds Postgres to 5432 and Redis to 6379. If you already have something on
> those ports, stop it first or change the ports.

### Running the tests

```bash
./mvnw test
```

Docker needs to be running, the integration tests start their own throwaway Postgres and Redis containers.

## API

### Create an account

`POST /api/v1/accounts`

```bash
curl -i -X POST http://localhost:8080/api/v1/accounts \
  -H 'Content-Type: application/json' \
  -d '{"customerName": "Jane Doe", "nickname": "Holiday fund"}'
```

```http
HTTP/1.1 201 Created
Location: http://localhost:8080/api/v1/accounts/5b0c8a4e-3f7e-4c4a-9d55-0f2f4a1b7e21
Content-Type: application/json

{
  "id": "5b0c8a4e-3f7e-4c4a-9d55-0f2f4a1b7e21",
  "accountNumber": "482901736455",
  "customerName": "Jane Doe",
  "nickname": "Holiday fund",
  "createdAt": "2026-11-14T04:30:12.345678Z"
}
```

| Field          | Required | Rules                                                                                     |
|----------------|----------|-------------------------------------------------------------------------------------------|
| `customerName` | yes      | not blank, max 100 characters                                                             |
| `nickname`     | no       | 5 to 30 characters, letters/numbers/single spaces/`. _ ' -`, must not be offensive        |

### Get an account

`GET /api/v1/accounts/{id}`

```bash
curl http://localhost:8080/api/v1/accounts/5b0c8a4e-3f7e-4c4a-9d55-0f2f4a1b7e21
```

### Errors

When something goes wrong you get a JSON body with a short `title` and a `detail` message explaining what happened.

| Status | When                                                                 |
|--------|----------------------------------------------------------------------|
| 400    | Validation failed, malformed JSON, or the id isn't a UUID            |
| 404    | Account not found                                                    |
| 422    | Customer already has 5 accounts                                      |
| 503    | Database unavailable (with a `Retry-After` header)                    |

Validation errors list every failing field:

```json
{
  "title": "Invalid request",
  "status": 400,
  "detail": "Request validation failed",
  "instance": "/api/v1/accounts",
  "errors": [
    { "field": "nickname", "message": "nickname must be between 5 and 30 characters" }
  ]
}
```

## Design notes and assumptions

Some requirements were open to interpretation, so these are the calls I made:

- **Identifying a customer.** The account model only has a customer name, so the 5 account limit
  is enforced per name, compared case-insensitively with extra whitespace collapsed (`" jane  DOE "`
  counts as `"Jane Doe"`). In a real system this would be a customer id from authentication.
- **Concurrent requests.** Checking the count and then inserting is a race: two requests at the same
  time could both see 4 accounts and both insert. Account creation takes a per-customer Postgres
  advisory lock (`pg_advisory_xact_lock`) for the duration of the transaction, so requests for the
  same customer are serialised while different customers don't block each other. There is a test
  that fires 10 concurrent requests and checks exactly 5 succeed.
- **Account number.** 12 digits: 11 random digits plus a Luhn check digit, so typos can be detected
  client side. Uniqueness is guaranteed by a unique constraint, the service also checks before
  inserting and retries in the (very unlikely) case of a clash.
- **Id.** A UUID, so ids can't be guessed or used to count accounts.
- **Offensive nicknames.** Checked against a word list in `src/main/resources/offensive-nicknames.txt`
  using a custom Bean Validation constraint (`@NotOffensive`). Matching is on whole words after
  undoing simple character swaps (`1d10t` -> `idiot`), and also with separators removed (`i.d.i.o.t`).
  Whole-word matching avoids false positives like "Classic Savings" (contains "ass").
- **Nickname format.** Besides length, the nickname can't be just spaces or have leading/trailing/double
  spaces, otherwise `"     "` would pass the 5 character minimum.
- **Single table.** Everything lives in `savings_account`. The schema is created by Flyway
  (`src/main/resources/db/migration`), and Hibernate only validates it.

### Caching

`GET /api/v1/accounts/{id}` is cached in Redis for 10 minutes (configurable with `app.cache.accounts-ttl`),
stored as JSON. Accounts can't be changed after creation, so there's nothing to invalidate yet.
Not-found results aren't cached.

If Redis is down the error is logged and the request falls through to Postgres, so a cache outage
doesn't take the API down.

### Database unavailable

- The connection pool timeout is 3 seconds, so requests fail fast instead of hanging for the default 30.
- Connection failures are returned as `503 Service Unavailable` with `Retry-After`, rather than a 500.
- Accounts that are already cached can still be fetched while the database is down.
- `DatabaseOutageIntegrationTest` covers this by stopping the Postgres container part way through a test.

The app does need the database to be up at startup (Flyway runs on boot).

## Tests

| Test                               | What it covers                                                          |
|------------------------------------|-------------------------------------------------------------------------|
| `OffensiveWordFilterTest`          | Word matching, character substitutions, false positives                 |
| `AccountNumberGeneratorTest`       | Format and Luhn check digit                                             |
| `SavingsAccountServiceTest`        | Account limit, name normalisation, account number retries (Mockito)     |
| `SavingsAccountControllerTest`     | Request validation and error responses (`@WebMvcTest`)                  |
| `SavingsAccountApiIntegrationTest` | Full flow against Postgres + Redis, caching, concurrent account limit   |
| `DatabaseOutageIntegrationTest`    | 503s and cached reads when Postgres goes down                           |

## What I'd add for production

Some of these are also marked as `TODO`s in the code:

- Authentication, and only letting customers read their own accounts
- Using a real customer id instead of the name for the account limit
- An `Idempotency-Key` header on `POST` so client retries don't open duplicate accounts
- Account numbers from the core banking system (branch/product prefixes)
- A maintained offensive word list or moderation service, reloadable without a deploy
- A circuit breaker around Redis so an outage doesn't cost a timeout on every request
- Rate limiting on account creation
- Metrics, tracing and an OpenAPI spec
