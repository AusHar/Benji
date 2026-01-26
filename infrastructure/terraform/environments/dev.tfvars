# Development environment configuration
# Usage: terraform apply -var-file=environments/dev.tfvars

environment = "dev"
aws_region  = "us-east-1"

# Database (minimal for dev)
db_instance_class    = "db.t3.micro"
db_allocated_storage = 20

# Container (minimal for dev)
container_cpu    = 256
container_memory = 512
min_instances    = 1
max_instances    = 1

# Security
enable_deletion_protection = false

# Tags
tags = {
  CostCenter = "development"
}
