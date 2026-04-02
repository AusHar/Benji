# Runbook

## 1. Service Overview
Benji Trader Assistant is a Spring Boot API deployed to AWS Lightsail (Ubuntu 22.04) that aggregates
market data, portfolio balances, trade tracking, trading journal, and personal finance signals. It exposes REST endpoints under `/api/*`,
uses Postgres for persistence, and integrates with Yahoo Finance for market data (no API key required). A single-file SPA is served
from the JAR at the root path. The app is multi-tenant — all data is scoped by user_id. A demo mode allows visitors to explore with isolated sample data.

## 2. Environments
- **Local (dev):** Run via `./gradlew bootRun` with the `dev` profile; uses fake market data and in-memory H2 storage.
- **Test (CI):** `./gradlew test` with Testcontainers-managed Postgres; executed in GitHub Actions.
- **Prod:** JAR deployed to Lightsail via rsync in CI, running as the `benji` systemd service with the `prod` profile, Yahoo Finance provider, and Postgres.

## 3. Access & Credentials
| Secret | Location | Notes |
| --- | --- | --- |
| `TRADING_API_KEY` | `/etc/systemd/system/benji.service` | Required for prod API key filter. |
| `SPRING_DATASOURCE_PASSWORD` | `/etc/systemd/system/benji.service` | Postgres password. |
| `MANAGEMENT_PASSWORD` | `/etc/systemd/system/benji.service` | Basic auth for `/actuator/*`. |
| `LIGHTSAIL_SSH_KEY` | GitHub Actions secret | Base64-encoded PEM for deploy SSH access. |

Prod startup validates required secrets and rejects placeholder values (e.g., `changeMe`, `demo`, `replace_me`).

## 4. Operations
- **Health check:** `GET https://port.adhdquants.com/actuator/health`
  - Returns `{"status":"UP"}` when healthy.
  - Returns `{"status":"UNKNOWN"}` when Yahoo Finance is temporarily unreachable (this is normal, not an outage).
  - Returns `{"status":"DOWN"}` only on unexpected application errors.
- **Logs:** `sudo journalctl -u benji -f` on the Lightsail instance.

## 5. Deployment Procedure
Pushes to `main` auto-deploy via GitHub Actions (`ci.yml`):
1. Gradle builds and tests the JAR (`spotlessCheck build --no-daemon`).
2. Previous JAR is backed up on the server (`benji.jar.prev`).
3. New JAR is rsynced to `ubuntu@107.22.236.28:/opt/benji/benji.jar`.
4. `sudo systemctl restart benji` is run over SSH.
5. CI polls `https://port.adhdquants.com/actuator/health` (20 attempts × 15s = up to 5 minutes).
6. **On health check failure:** CI automatically rolls back to `benji.jar.prev` and restarts the service.

To deploy manually:
```bash
ssh -i ~/.ssh/benji-lightsail.pem ubuntu@107.22.236.28
sudo systemctl restart benji
sudo journalctl -u benji -f
```

## 6. Rollback Procedure
- **Automatic:** CI rolls back on health check failure (see above).
- **Manual (git):** Revert the commit on `main` and push — CI will redeploy the previous JAR.
- **Manual (SSH):** `ssh ubuntu@107.22.236.28` then `cp /opt/benji/benji.jar.prev /opt/benji/benji.jar && sudo systemctl restart benji`.

## 7. Troubleshooting

### Application fails to start
- `sudo journalctl -u benji --since "5 min ago"` — look for Spring Boot startup errors.
- Common causes: missing env var (`MANAGEMENT_PASSWORD` has no default), Postgres unreachable, Flyway migration failure.

### Health check returns 503 after deploy
- App may still be starting; wait 30s and retry.
- If persistent: check journal for startup exceptions.

### Health check returns UNKNOWN
- Normal — Yahoo Finance was temporarily unreachable at the time of the health probe. The service is operational.

### Quote endpoint returning stale data
- Cache TTLs: quotes 30s, overviews 4h, history 1h, news 15m. Restart to flush.

### Demo mode issues
- Demo endpoint: `POST /api/demo/session` (public, no auth). Returns `{"apiKey": "demo"}`.
- Rate limited to one reset per 5 seconds (configurable via `demo.cooldown-ms`).
- If demo data appears corrupt or missing, hit the endpoint to reset — it deletes and reseeds all demo user data (portfolio, trades, finance, journal).
- Demo user is isolated from the owner/admin user — demo resets do not affect real data.

## 8. Onboarding Checklist
- Install Java 21, copy `ENV.example` and populate variables.
- No market data API key required — Yahoo Finance is free and unauthenticated.
- Run `./gradlew clean build` from `apps/api/trader-assistant/trading-dashboard`.
- Review `docs/ARCHITECTURE.md` for architectural context.
