variable "profile" {
  description = "AWS CLI profile name"
  type        = string
  default     = "eks-role"
}

variable "region" {
  description = "AWS region"
  type        = string
  default     = "ap-northeast-2"
}

variable "project_name" {
  description = "Project prefix for naming"
  type        = string
  default     = "wearhouse"
}

variable "environment" {
  description = "Environment name"
  type        = string
  default     = "prod"
}

variable "cluster_name" {
  description = "EKS cluster name"
  type        = string
  default     = "wearhouse-eks"
}

variable "manage_network" {
  description = "Create VPC/subnets with Terraform"
  type        = bool
  default     = false
}

variable "manage_eks" {
  description = "Create EKS cluster with Terraform"
  type        = bool
  default     = false
}

variable "manage_addons" {
  description = "Install ALB controller/Argo CD/metrics with Terraform"
  type        = bool
  default     = false
}

variable "manage_metrics_server" {
  description = "Manage metrics-server via Helm (disable when EKS already provides it)"
  type        = bool
  default     = false
}

variable "create_ecr_repositories" {
  description = "Create ECR repositories (false when repositories already exist)"
  type        = bool
  default     = false
}

variable "existing_vpc_id" {
  description = "Existing VPC ID to reuse when manage_network=false"
  type        = string
  default     = "vpc-057b04df55b68b503"
}

variable "existing_vpc_cidr" {
  description = "Existing VPC CIDR to use for SG ingress when manage_eks=false"
  type        = string
  default     = "192.168.0.0/16"
}

variable "existing_public_subnet_ids" {
  description = "Existing public subnets to reuse when manage_network=false"
  type        = list(string)
  default = [
    "subnet-00a9850d425e2f17e",
    "subnet-08935dfdba56f84ae",
    "subnet-0d55496ac9563d086"
  ]
}

variable "existing_private_subnet_ids" {
  description = "Existing private subnets to reuse when manage_network=false"
  type        = list(string)
  default = [
    "subnet-0eabe297d7e441ac6",
    "subnet-07d48ff56c6f6d0a3",
    "subnet-060a425017c4c72b7"
  ]
}

variable "existing_data_subnet_ids" {
  description = "Existing data subnets to reuse when manage_network=false (optional)"
  type        = list(string)
  default     = []
}

variable "vpc_cidr" {
  description = "VPC CIDR"
  type        = string
  default     = "10.30.0.0/16"
}

variable "azs" {
  description = "AZ list"
  type        = list(string)
  default     = ["ap-northeast-2a", "ap-northeast-2b", "ap-northeast-2c"]
}

variable "public_subnet_cidrs" {
  description = "Public subnet CIDRs aligned to azs"
  type        = list(string)
  default     = ["10.30.0.0/20", "10.30.16.0/20", "10.30.32.0/20"]
}

variable "private_subnet_cidrs" {
  description = "Private subnet CIDRs aligned to azs"
  type        = list(string)
  default     = ["10.30.64.0/20", "10.30.80.0/20", "10.30.96.0/20"]
}

variable "data_subnet_cidrs" {
  description = "Data subnet CIDRs aligned to azs"
  type        = list(string)
  default     = ["10.30.128.0/20", "10.30.144.0/20", "10.30.160.0/20"]
}

variable "node_instance_types" {
  description = "EKS managed node group instance types"
  type        = list(string)
  default     = ["t3.large"]
}

variable "node_desired_size" {
  description = "Desired node count"
  type        = number
  default     = 2
}

variable "node_min_size" {
  description = "Minimum node count"
  type        = number
  default     = 2
}

variable "node_max_size" {
  description = "Maximum node count"
  type        = number
  default     = 4
}

variable "service_names" {
  description = "ECR repositories to create"
  type        = list(string)
  default = [
    "service-discovery",
    "config-server",
    "auth",
    "user",
    "product",
    "cart",
    "order",
    "payment",
    "inventory",
    "api-gateway"
  ]
}

variable "db_name" {
  description = "RDS database name"
  type        = string
  default     = "wearhouse"
}

variable "db_username" {
  description = "RDS admin username"
  type        = string
  default     = "wearhouse"
}

variable "db_password" {
  description = "RDS admin password"
  type        = string
  sensitive   = true
}

variable "db_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t4g.medium"
}

variable "kafka_instance_type" {
  description = "MSK broker instance type"
  type        = string
  default     = "kafka.t3.small"
}

variable "kafka_volume_size" {
  description = "MSK broker EBS volume size"
  type        = number
  default     = 200
}

variable "extra_tags" {
  description = "Additional tags"
  type        = map(string)
  default     = {}
}

variable "ssm_secure_parameters" {
  description = "SecureString SSM parameters"
  type        = map(string)
  default     = {}
  sensitive   = true
}

variable "ssm_plain_parameters" {
  description = "Plain String SSM parameters"
  type        = map(string)
  default     = {}
}
