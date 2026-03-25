output "arn" {
  value = aws_msk_cluster.msk.arn
}

output "bootstrap_brokers" {
  value = aws_msk_cluster.msk.bootstrap_brokers
}

output "bootstrap_brokers_sasl_iam" {
  value = aws_msk_cluster.msk.bootstrap_brokers_sasl_iam
}
