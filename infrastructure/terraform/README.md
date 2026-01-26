# Benji Infrastructure

Terraform configuration for deploying Benji Trading Dashboard to AWS.

## Architecture

```
                                    ┌─────────────────────┐
                                    │   AWS App Runner    │
                                    │  (Auto-scaling)     │
                                    └──────────┬──────────┘
                                               │
┌──────────────────────────────────────────────┼──────────────────────────────────────────────┐
│                                    VPC       │                                              │
│  ┌─────────────────────────────────┼─────────────────────────────────────────────────────┐  │
│  │          Private Subnets        │                                                     │  │
│  │                                 │                                                     │  │
│  │    ┌────────────────────────────┼─────────────────────────────┐                       │  │
│  │    │      VPC Connector         │                             │                       │  │
│  │    │                            ▼                             │                       │  │
│  │    │              ┌─────────────────────────┐                 │                       │  │
│  │    │              │    RDS PostgreSQL       │                 │                       │  │
│  │    │              │    (Multi-AZ in prod)   │                 │                       │  │
│  │    │              └─────────────────────────┘                 │                       │  │
│  │    └──────────────────────────────────────────────────────────┘                       │  │
│  └───────────────────────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────────────────┘
```

## Prerequisites

1. AWS CLI configured with appropriate credentials
2. Terraform >= 1.5.0
3. Docker for building container images

## Quick Start

### 1. Initialize Terraform

```bash
cd infrastructure/terraform
terraform init
```

### 2. Create secrets file

Create a `secrets.tfvars` file (DO NOT commit this file):

```hcl
alpha_vantage_api_key = "your-alphavantage-key"
trading_api_key       = "your-secure-api-key"
management_password   = "your-actuator-password"
```

### 3. Deploy to dev

```bash
terraform plan \
  -var-file=environments/dev.tfvars \
  -var-file=secrets.tfvars

terraform apply \
  -var-file=environments/dev.tfvars \
  -var-file=secrets.tfvars
```

### 4. Build and push Docker image

After Terraform creates the ECR repository:

```bash
# Get ECR login
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin <account-id>.dkr.ecr.us-east-1.amazonaws.com

# Build the image
cd apps/api/trader-assistant/trading-dashboard
docker build -t benji-app .

# Tag and push
docker tag benji-app:latest <ecr-repo-url>:latest
docker push <ecr-repo-url>:latest
```

## Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `alpha_vantage_api_key` | AlphaVantage API key | Yes |
| `trading_api_key` | API key for authenticating requests | Yes |
| `management_password` | Password for actuator endpoints | Yes |

## Costs (Estimated)

### Development (~$30/month)
- App Runner: ~$5/month (minimal usage)
- RDS db.t3.micro: ~$15/month
- Secrets Manager: ~$1/month
- Data transfer: ~$5/month

### Production (~$80/month)
- App Runner: ~$20/month (with scaling)
- RDS db.t3.small Multi-AZ: ~$50/month
- Secrets Manager: ~$1/month
- Data transfer: ~$10/month

## Cleanup

```bash
terraform destroy \
  -var-file=environments/dev.tfvars \
  -var-file=secrets.tfvars
```

## Security Notes

1. Database is in private subnets (not publicly accessible)
2. All secrets are stored in AWS Secrets Manager
3. App Runner uses IAM roles for secret access
4. RDS storage is encrypted at rest
5. All traffic is HTTPS (App Runner provides TLS)
