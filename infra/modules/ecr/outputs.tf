output "registry_url" {
  value = "${data.aws_caller_identity.current.account_id}.dkr.ecr.${data.aws_region.current.name}.amazonaws.com"
}

output "repository_urls" {
  value = var.create_repositories ? {
    for name, repo in aws_ecr_repository.this :
    name => repo.repository_url
    } : {
    for name in var.repository_names :
    name => "${data.aws_caller_identity.current.account_id}.dkr.ecr.${data.aws_region.current.name}.amazonaws.com/${name}"
  }
}
