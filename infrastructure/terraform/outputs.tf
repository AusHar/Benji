# -----------------------------------------------------------------------------
# Outputs
# -----------------------------------------------------------------------------

output "app_url" {
  description = "URL of the deployed application"
  value       = "https://${aws_apprunner_service.main.service_url}"
}

output "ecr_repository_url" {
  description = "ECR repository URL for pushing Docker images"
  value       = aws_ecr_repository.main.repository_url
}

output "database_endpoint" {
  description = "RDS database endpoint"
  value       = aws_db_instance.main.address
  sensitive   = false
}

output "database_port" {
  description = "RDS database port"
  value       = aws_db_instance.main.port
}

output "database_name" {
  description = "Database name"
  value       = var.db_name
}

output "secrets_manager_db_arn" {
  description = "ARN of the database credentials secret"
  value       = aws_secretsmanager_secret.db_credentials.arn
}

output "secrets_manager_app_arn" {
  description = "ARN of the application secrets"
  value       = aws_secretsmanager_secret.app_secrets.arn
}

output "vpc_id" {
  description = "VPC ID"
  value       = aws_vpc.main.id
}

output "private_subnet_ids" {
  description = "Private subnet IDs"
  value       = aws_subnet.private[*].id
}

output "public_subnet_ids" {
  description = "Public subnet IDs"
  value       = aws_subnet.public[*].id
}

output "apprunner_service_arn" {
  description = "ARN of the App Runner service"
  value       = aws_apprunner_service.main.arn
}

output "cloudwatch_log_group" {
  description = "CloudWatch log group name"
  value       = aws_cloudwatch_log_group.app.name
}

# -----------------------------------------------------------------------------
# Deployment Instructions
# -----------------------------------------------------------------------------

output "deployment_instructions" {
  description = "Instructions for deploying the application"
  value       = <<-EOT

    ============================================
    Benji Trading Dashboard - Deployment Guide
    ============================================

    1. Build and push Docker image:

       # Authenticate with ECR
       aws ecr get-login-password --region ${var.aws_region} | docker login --username AWS --password-stdin ${aws_ecr_repository.main.repository_url}

       # Build the image
       cd apps/api/trader-assistant/trading-dashboard
       docker build -t ${aws_ecr_repository.main.repository_url}:latest .

       # Push to ECR
       docker push ${aws_ecr_repository.main.repository_url}:latest

    2. App Runner will automatically deploy when you push a new image.

    3. Access your application at:
       https://${aws_apprunner_service.main.service_url}

    4. View logs in CloudWatch:
       Log Group: ${aws_cloudwatch_log_group.app.name}

    5. Health check endpoint:
       https://${aws_apprunner_service.main.service_url}/actuator/health

    ============================================
  EOT
}
