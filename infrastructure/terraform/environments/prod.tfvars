# Production environment configuration
# Usage: terraform apply -var-file=environments/prod.tfvars

environment = "prod"
aws_region  = "us-east-1"

# Database (production-ready)
db_instance_class    = "db.t3.small"
db_allocated_storage = 50

# Container (production-ready)
container_cpu    = 512
container_memory = 1024
min_instances    = 1
max_instances    = 3

# Security
enable_deletion_protection = true

# Tags
tags = {
  CostCenter = "production"
}
