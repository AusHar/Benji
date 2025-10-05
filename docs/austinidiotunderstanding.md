Mental model
	•	Docker image: your app plus runtime. Built by GitHub Actions.
	•	ECR: private image registry that stores those images. Target of your CI push.
	•	IAM + OIDC: lets GitHub Actions assume a role in your AWS account without long-lived keys.
	•	ECS Fargate: runs containers on AWS managed compute. You define a task. A service keeps it running.
	•	ALB: load balancer that exposes your service on the internet and does health checks.
	•	RDS PostgreSQL: managed Postgres for your app data.
	•	Secrets Manager / SSM: store credentials. ECS injects them as env vars at run time.