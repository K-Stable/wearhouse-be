variable "secure_parameters" {
  type      = map(string)
  default   = {}
  sensitive = true
}

variable "plain_parameters" {
  type    = map(string)
  default = {}
}

variable "tags" {
  type    = map(string)
  default = {}
}
