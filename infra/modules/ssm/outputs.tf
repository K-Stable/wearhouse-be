output "secure_parameter_names" {
  value = keys(aws_ssm_parameter.secure)
}

output "plain_parameter_names" {
  value = keys(aws_ssm_parameter.plain)
}
