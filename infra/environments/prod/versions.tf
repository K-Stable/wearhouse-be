terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.50"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.24"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.12"
    }
  }

  # NOTE:
  # Backend is intentionally omitted for now because the current IAM user
  # does not have S3 bucket creation permissions.
  # Re-enable `backend "s3" {}` after backend bucket/table are prepared.
}
