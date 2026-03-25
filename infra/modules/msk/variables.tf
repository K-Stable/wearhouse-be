variable "cluster_name" {
  type = string
}

variable "kafka_version" {
  type    = string
  default = "3.6.0"
}

variable "number_of_broker_nodes" {
  type    = number
  default = 3
}

variable "subnet_ids" {
  type = list(string)
}

variable "security_group_ids" {
  type = list(string)
}

variable "broker_instance_type" {
  type    = string
  default = "kafka.t3.small"
}

variable "ebs_volume_size" {
  type    = number
  default = 200
}

variable "log_retention_days" {
  type    = number
  default = 7
}

variable "tags" {
  type    = map(string)
  default = {}
}
