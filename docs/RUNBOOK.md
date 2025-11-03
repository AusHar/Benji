+# Runbook
+
+## 1. Service Overview
+Benji Trader Assistant is a Spring Boot API deployed to AWS ECS that aggregates market data, portfolio balances, and personal finance signals. It exposes REST endpoints under `/api/*`, uses Postgres for persistence, and integrates with external market-data providers (AlphaVantage primary, fake provider for dev/testing).
+
+## 2. Environments
+- **Local (dev):** Run via `./gradlew bootRun` with the `dev` profile; uses fake market data and in-memory/H2 storage unless Postgres variables are set.
+- **Test (CI):** `./gradlew test` with Testcontainers-managed Postgres; executed in GitHub Actions.
+- **Staging/Prod:** Docker image pushed to ECR and deployed to ECS using the `prod` profile, real providers, and managed Postgres.
+
+## 3. Access & Credentials
+| Secret | Location | Notes |
+| --- | --- | --- |
+| `trading.api.key` | AWS SSM Parameter / Secrets Manager | Required for prod API key filter. Map to `TRADING_API_KEY` env var. |
+| `SPRING_DATASOURCE_URL`, `USERNAME`, `PASSWORD` | AWS SSM/Secrets Manager | JDBC connection for Postgres. |
+| `ALPHAVANTAGE_API_KEY` | AWS SSM/Secrets Manager | External provider key. |
+| `ACTUATOR_USERNAME` / `ACTUATOR_PASSWORD` | AWS SSM/Secrets Manager | Basic auth for `/actuator/*` endpoints when secured. |
+
+Store secrets in the GitHub environment (for CI) as references to AWS via OIDC or pre-populated environment variables in ECS task definitions.
+
+## 4. Operations Dashboard
+- **Health checks:**
+  - Application: `GET https://<service-domain>/actuator/health`
+  - Liveness/Readiness can be mapped to ECS health checks via ALB.
+- **Metrics:** `GET https://<service-domain>/actuator/metrics` (requires actuator credentials).
+- **Logs:** CloudWatch log group from ECS task definition; ensure retention is at least 30 days.
+
+## 5. Deployment Procedure
+1. Ensure `main` branch is green (tests passing) and version bump applied if needed.
+2. Tag the release (`git tag vX.Y.Z && git push --tags`) or trigger the `deploy.yml` workflow manually.
+3. GitHub Actions job `deploy` will:
+   - Build the Gradle project (`./gradlew build`).
+   - Build/push Docker image to `${ECR_REPOSITORY}`.
+   - Update ECS service via AWS CLI using IAM role `${ACTIONS_ROLE_ARN}`.
+4. Monitor GitHub Actions logs for success and ECS events for task rollout.
+5. Validate `/actuator/health` and run smoke tests (quote retrieval, portfolio summary) post-deploy.
+
+## 6. Rollback Procedure
+- **Preferred:** Re-deploy the previous known-good tag using the same workflow (`git tag vX.Y.Z --force` not recommended; instead trigger workflow with prior tag).
+- **Manual fallback:** Update ECS service to point to prior image in ECR via AWS console/CLI (`aws ecs update-service --force-new-deployment --task-definition <previous>`).
+- Verify health, then retroactively tag the reverted state for traceability.
+
+## 7. Monitoring & Alerting
+- Set CloudWatch alarms on:
+  - ECS service `CPUUtilization` and `MemoryUtilization` thresholds.
+  - Actuator health check failures (ALB 5xx or health-check alarm).
+  - Error log patterns (e.g., provider failure spikes).
+- Integrate alarms with SNS/Slack/Email.
+- Track business metrics (quote latency, portfolio ingestion success) via custom Micrometer timers/counters once implemented.
+
+## 8. Troubleshooting
+
+### 8.1 Application fails to start
+- Check ECS task logs for Spring profile misconfiguration or missing env vars.
+- Common issues:
+  - `UnsatisfiedDependencyException` → Verify secrets for provider keys and datasource.
+  - `FlywayException` → Ensure database reachable and schema permissions correct.
+
+### 8.2 Quote endpoint returning stale data
+1. Inspect application logs for provider errors or rate limits.
+2. Validate cache configuration (`trading.cache.ttl`).
+3. Manually clear cache via application restart (future enhancement: actuator endpoint for cache eviction).
+
+### 8.3 External provider outages
+- System should fall back to cached data; confirm provider error handling logs.
+- Temporarily reduce quote requests or switch to secondary provider configuration if available.
+
+### 8.4 Database connectivity issues
+- Confirm RDS/managed Postgres availability and security groups.
+- Rotate credentials in Secrets Manager and re-deploy.
+- Run Flyway repair/migrate if schema drift detected.
+
+## 9. Onboarding Checklist
+- Install Java 21, Docker, and Gradle wrapper available.
+- Copy `apps/api/trader-assistant/trading-dashboard/ENV.example` and populate environment variables.
+- Run `./gradlew clean build` from the API module to validate setup.
+- Review `docs/ARCHITECTURE.md` and `docs/TEST_PLAN.md` for deeper context.
+
+## 10. Incident Response
+- Declare severity based on impact (quote outage vs. full API downtime).
+- Create incident log (timestamp, symptoms, actions taken).
+- Mitigate (scale ECS tasks, rollback, patch configuration).
+- Post-incident: document root cause, update runbook/automation, add tests to prevent regression.
