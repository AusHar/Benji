# -----------------------------------------------------------------------------
# Required Variables
# -----------------------------------------------------------------------------

variable "aws_region" {
  description = "AWS region to deploy resources"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be one of: dev, staging, prod"
  }
}

variable "app_name" {
  description = "Application name used for resource naming"
  type        = string
  default     = "benji"
}

# -----------------------------------------------------------------------------
# Database Variables
# -----------------------------------------------------------------------------

variable "db_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t3.micro"
}

variable "db_allocated_storage" {
  description = "Allocated storage in GB for RDS"
  type        = number
  default     = 20
}

variable "db_name" {
  description = "Name of the PostgreSQL database"
  type        = string
  default     = "trader"
}

variable "db_username" {
  description = "Master username for the database"
  type        = string
  default     = "trader"
}

# -----------------------------------------------------------------------------
# Container/App Runner Variables
# -----------------------------------------------------------------------------

variable "container_port" {
  description = "Port the container listens on"
  type        = number
  default     = 8080
}

variable "container_cpu" {
  description = "CPU units for the container (1024 = 1 vCPU)"
  type        = number
  default     = 256
}

variable "container_memory" {
  description = "Memory in MB for the container"
  type        = number
  default     = 512
}

variable "min_instances" {
  description = "Minimum number of container instances"
  type        = number
  default     = 1
}

variable "max_instances" {
  description = "Maximum number of container instances"
  type        = number
  default     = 2
}

# -----------------------------------------------------------------------------
# API Configuration Variables
# -----------------------------------------------------------------------------

variable "alpha_vantage_api_key" {
  description = "AlphaVantage API key for market data"
  type        = string
  sensitive   = true
}

variable "trading_api_key" {
  description = "API key for authenticating requests to Benji"
  type        = string
  sensitive   = true
}

variable "management_password" {
  description = "Password for actuator endpoints"
  type        = string
  sensitive   = true
}

# -----------------------------------------------------------------------------
# Optional Variables
# -----------------------------------------------------------------------------

variable "domain_name" {
  description = "Custom domain name for the application (optional)"
  type        = string
  default     = ""
}

variable "enable_deletion_protection" {
  description = "Enable deletion protection for RDS"
  type        = bool
  default     = false
}

variable "tags" {
  description = "Additional tags to apply to all resources"
  type        = map(string)
  default     = {}
}
