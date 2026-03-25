output "vpc_id" {
  value = local.active_vpc_id
}

output "public_subnet_ids" {
  value = local.active_public_subnet_ids
}

output "private_subnet_ids" {
  value = local.active_private_subnet_ids
}

output "data_subnet_ids" {
  value = local.active_data_subnet_ids
}

output "cluster_name" {
  value = var.cluster_name
}

output "cluster_endpoint" {
  value = data.aws_eks_cluster.this.endpoint
}

output "oidc_provider_arn" {
  value = var.manage_eks ? module.eks[0].oidc_provider_arn : try(data.aws_iam_openid_connect_provider.existing[0].arn, null)
}

output "alb_controller_irsa_role_arn" {
  value = var.manage_addons ? module.alb_irsa_role[0].iam_role_arn : null
}

output "ecr_registry_url" {
  value = module.ecr.registry_url
}

output "ecr_repository_urls" {
  value = module.ecr.repository_urls
}

output "rds_endpoint" {
  value = module.rds.endpoint
}

output "rds_port" {
  value = module.rds.port
}

output "msk_bootstrap_brokers_sasl_iam" {
  value = module.msk.bootstrap_brokers_sasl_iam
}

output "argocd_namespace" {
  value = var.manage_addons ? "argocd" : null
}
