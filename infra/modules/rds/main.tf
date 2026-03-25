module "db" {
  source  = "terraform-aws-modules/rds/aws"
  version = "~> 6.13"

  identifier = var.identifier

  engine               = "mysql"
  engine_version       = var.engine_version
  family               = "mysql8.0"
  major_engine_version = "8.0"
  instance_class       = var.instance_class

  allocated_storage     = var.allocated_storage
  max_allocated_storage = var.max_allocated_storage

  db_name  = var.db_name
  username = var.username
  password = var.password
  port     = 3306

  create_db_subnet_group = true
  subnet_ids             = var.subnet_ids

  vpc_security_group_ids = var.vpc_security_group_ids

  multi_az                = var.multi_az
  deletion_protection     = var.deletion_protection
  skip_final_snapshot     = var.skip_final_snapshot
  backup_retention_period = var.backup_retention_period

  performance_insights_enabled = true
  monitoring_interval          = 0

  tags = var.tags
}
