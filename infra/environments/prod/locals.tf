locals {
  common_tags = merge(
    {
      Project     = var.project_name
      Environment = var.environment
      ManagedBy   = "terraform"
    },
    var.extra_tags
  )

  ssm_base_path = "/${var.project_name}/${var.environment}"

  ssm_secure_full = {
    for k, v in var.ssm_secure_parameters :
    "${local.ssm_base_path}/${k}" => v
  }

  ssm_plain_full = {
    for k, v in var.ssm_plain_parameters :
    "${local.ssm_base_path}/${k}" => v
  }
}
