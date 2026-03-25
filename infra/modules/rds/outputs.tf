output "endpoint" {
  value = module.db.db_instance_endpoint
}

output "address" {
  value = module.db.db_instance_address
}

output "port" {
  value = module.db.db_instance_port
}

output "db_name" {
  value = module.db.db_instance_name
}
