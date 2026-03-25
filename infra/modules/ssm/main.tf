resource "aws_ssm_parameter" "secure" {
  for_each = nonsensitive(var.secure_parameters)

  name  = each.key
  type  = "SecureString"
  value = each.value

  tags = var.tags
}

resource "aws_ssm_parameter" "plain" {
  for_each = var.plain_parameters

  name  = each.key
  type  = "String"
  value = each.value

  tags = var.tags
}
