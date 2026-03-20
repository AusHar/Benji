# Runbook

## 1. Service Overview
Benji Trader Assistant is a Spring Boot API deployed to AWS Lightsail (Ubuntu 22.04) that aggregates
market data, portfolio balances, and personal finance signals. It exposes REST endpoints under `/api/*`,
uses Postgres for persistence, and integrates with Yahoo Finance and CoinGecko for market data (no API key required). A single-file SPA is served
from the JAR at the root path.

## 2. Environments
- **Local (dev):** Run via `./gradlew bootRun` with the `dev` profile; uses fake market data and in-memory H2 storage.
- **Test (CI):** `./gradlew test` with Testcontainers-managed Postgres; executed in GitHub Actions.
- **Prod:** JAR deployed to Lightsail via rsync in CI, running as the `benji` systemd service with the `prod` profile, Yahoo Finance + CoinGecko market data, and Postgres.

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
  - Returns `{"status":"UNKNOWN"}` when the market data per-minute quota is exhausted (this is normal, not an outage).
  - Returns `{"status":"DOWN"}` only when the market data API is unreachable.
- **Quota usage:** `GET https://port.adhdquants.com/api/marketdata/quota` (requires `X-API-KEY` header)
- **Logs:** `sudo journalctl -u benji -f` on the Lightsail instance.

## 5. Deployment Procedure
Pushes to `main` auto-deploy via GitHub Actions (`ci.yml`):
1. Gradle builds and tests the JAR (`spotlessCheck build --no-daemon`).
2. JAR is rsynced to `ubuntu@107.22.236.28:/opt/benji/benji.jar`.
3. `sudo systemctl restart benji` is run over SSH.
4. CI polls `https://port.adhdquants.com/actuator/health` for up to ~130 seconds (30s sleep + 10 retries × 10s).

To deploy manually:
```bash
ssh -i ~/.ssh/benji-lightsail.pem ubuntu@107.22.236.28
sudo systemctl restart benji
sudo journalctl -u benji -f
```

## 6. Rollback Procedure
- Revert the commit on `main` and push — CI will redeploy the previous JAR.
- Or SSH in and replace `/opt/benji/benji.jar` with the prior JAR, then `sudo systemctl restart benji`.

## 7. Troubleshooting

### Application fails to start
- `sudo journalctl -u benji --since "5 min ago"` — look for Spring Boot startup errors.
- Common causes: missing env var (`MANAGEMENT_PASSWORD` has no default), Postgres unreachable, Flyway migration failure.

### Health check returns 503 after deploy
- App may still be starting; wait 30s and retry.
- If persistent: check journal for startup exceptions.

### Health check returns UNKNOWN
- Normal — market data per-minute quota was exhausted at the time of the health probe. The service is operational.

### Quote endpoint returning stale data
- Check quota usage at `/api/marketdata/quota`.
- Cache TTLs: quotes 30s, overviews 4h, history 1h, news 15m. Restart to flush.

## 8. Onboarding Checklist
- Install Java 21, copy `ENV.example` and populate variables.
- No market data API key required — Yahoo Finance and CoinGecko are free and unauthenticated.
- Run `./gradlew clean build` from `apps/api/trader-assistant/trading-dashboard`.
- Review `docs/ARCHITECTURE.md` for architectural context.
