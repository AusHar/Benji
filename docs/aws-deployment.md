# AWS Deployment Guide

## Overview
This guide describes how to provision AWS infrastructure for the trading-dashboard service and deploy images produced by the CI workflow. The pipeline builds the Spring Boot container image via GitHub Actions and publishes it to Amazon ECR, ready for ECS, EKS, or Elastic Beanstalk.

```mermaid
digraph {
  rankdir=LR
  GitHub["GitHub Actions\nci.yml"] -> ECR["Amazon ECR\ntrading-dashboard repo"]
  ECR -> ECS["Amazon ECS Service"]
  ECR -> EKS["Amazon EKS Deployment"]
  ECR -> Beanstalk["Elastic Beanstalk\nDocker Platform"]
}
```

## Prerequisites
- AWS account with permissions to create IAM roles, ECR repositories, ECS/EKS clusters, and Elastic Beanstalk environments.
- Terraform or AWS CLI access (examples below use AWS CLI).
- GitHub repository administrator to configure secrets and variables.

## Step 1: Provision the Container Registry
1. Create an ECR repository to store trading-dashboard images:
   ```bash
   aws ecr create-repository \
     --repository-name trading-dashboard \
     --image-scanning-configuration scanOnPush=true \
     --region <aws-region>
   ```
2. Note the registry URI (`<account-id>.dkr.ecr.<region>.amazonaws.com/trading-dashboard`); it is used by deployment targets.

## Step 2: Configure IAM and GitHub Secrets
1. Create an IAM role that GitHub Actions can assume with `sts:AssumeRole` via OpenID Connect:
   ```bash
   aws iam create-role \
     --role-name GitHubTradingDashboardDeployRole \
     --assume-role-policy-document file://oidc-trust-policy.json
   ```
   Example trust policy (`oidc-trust-policy.json`):
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Effect": "Allow",
         "Principal": {
           "Federated": "arn:aws:iam::<account-id>:oidc-provider/token.actions.githubusercontent.com"
         },
         "Action": "sts:AssumeRoleWithWebIdentity",
         "Condition": {
           "StringEquals": {
             "token.actions.githubusercontent.com:sub": "repo:<org>/<repo>:environment:production"
           }
         }
       }
     ]
   }
   ```
2. Attach permissions for ECR (pull/push) and the target runtime (ECS/EKS/Beanstalk). Minimum policy example:
   ```bash
   aws iam attach-role-policy \
     --role-name GitHubTradingDashboardDeployRole \
     --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryPowerUser
   ```
   Add ECS/EKS/Beanstalk deployment policies as needed.
3. In the GitHub repository, create:
   - **Actions secret** `AWS_DEPLOY_ROLE_ARN` containing the role ARN.
   - **Actions variable** `AWS_REGION` with the deployment region (e.g., `us-east-1`).
   - **Actions variable** `ECR_REPOSITORY` (e.g., `trading-dashboard`).

## Step 3: Trigger Image Publication
- Manual run: `Actions → ci → Run workflow` (select branch). The publish job runs only for manual dispatches.
- Tagged release: push an annotated tag (e.g., `v1.0.0`). The workflow publishes an image tagged with the Git tag; non-tag runs use the commit SHA.

## Step 4: Deploy to Amazon ECS (Fargate)
1. Create a cluster:
   ```bash
   aws ecs create-cluster --cluster-name trading-dashboard
   ```
2. Define a task execution role with ECR pull permissions and optional secrets access (AWS Secrets Manager / SSM Parameter Store).
3. Register a task definition referencing the image URI and container port 8080. Example JSON fragment:
   ```json
   {
     "containerDefinitions": [
       {
         "name": "trading-dashboard",
         "image": "<account-id>.dkr.ecr.<region>.amazonaws.com/trading-dashboard:<tag>",
         "portMappings": [{"containerPort": 8080, "protocol": "tcp"}],
         "environment": [{"name": "SPRING_PROFILES_ACTIVE", "value": "prod"}]
       }
     ],
     "requiresCompatibilities": ["FARGATE"],
     "cpu": "512",
     "memory": "1024"
   }
   ```
4. Create a Fargate service referencing the task definition and, optionally, an Application Load Balancer.

## Step 5: Deploy to Amazon EKS
1. Ensure the cluster is connected via `kubectl`.
2. Create a Kubernetes namespace (e.g., `trading-dashboard`).
3. Create a Kubernetes secret with the registry credentials (if using IAM roles for service accounts, annotate the service account instead).
4. Apply a deployment manifest referencing the latest image:
   ```yaml
   apiVersion: apps/v1
   kind: Deployment
   metadata:
     name: trading-dashboard
     namespace: trading-dashboard
   spec:
     replicas: 2
     selector:
       matchLabels:
         app: trading-dashboard
     template:
       metadata:
         labels:
           app: trading-dashboard
       spec:
         containers:
           - name: trading-dashboard
             image: <account-id>.dkr.ecr.<region>.amazonaws.com/trading-dashboard:<tag>
             ports:
               - containerPort: 8080
             env:
               - name: SPRING_PROFILES_ACTIVE
                 value: prod
   ```
5. Expose the deployment with a LoadBalancer service or ingress controller.

## Step 6: Deploy to Elastic Beanstalk
1. Create an Elastic Beanstalk application and Docker platform environment.
2. Package the image reference in a `Dockerrun.aws.json` v2 file:
   ```json
   {
     "AWSEBDockerrunVersion": 2,
     "containerDefinitions": [
       {
         "name": "trading-dashboard",
         "image": "<account-id>.dkr.ecr.<region>.amazonaws.com/trading-dashboard:<tag>",
         "essential": true,
         "memory": 1024,
         "portMappings": [{"hostPort": 80, "containerPort": 8080}]
       }
     ]
   }
   ```
3. Upload the Dockerrun file via the Beanstalk console or CLI to roll out the new version.

## Runtime Secrets and Configuration
- Store sensitive configuration (database URLs, API keys) in AWS Secrets Manager or SSM Parameter Store and inject them via ECS task definitions, Kubernetes secrets, or Beanstalk environment variables.
- Set JVM tuning via `JAVA_TOOL_OPTIONS` if overriding the Dockerfile defaults.
- Confirm networking and security groups expose port 8080 only to trusted clients or load balancers.

## Verification Checklist
- [ ] Image exists in ECR with the expected tag.
- [ ] Target runtime (ECS/EKS/Beanstalk) pulls the new image version successfully.
- [ ] Application health checks (Spring actuator `/actuator/health`) succeed.
- [ ] Logs monitored through CloudWatch or preferred logging stack.
