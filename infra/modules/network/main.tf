locals {
  az_suffixes = [for az in var.azs : substr(az, length(az) - 1, 1)]
}

module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 5.17"

  name = "${var.name_prefix}-vpc"
  cidr = var.cidr

  azs              = var.azs
  public_subnets   = var.public_subnets
  private_subnets  = var.private_subnets
  database_subnets = var.database_subnets

  public_subnet_names   = [for suffix in local.az_suffixes : "${var.name_prefix}-public-${suffix}"]
  private_subnet_names  = [for suffix in local.az_suffixes : "${var.name_prefix}-private-${suffix}"]
  database_subnet_names = [for suffix in local.az_suffixes : "${var.name_prefix}-data-${suffix}"]

  create_database_subnet_group = true

  enable_nat_gateway     = var.enable_nat_gateway
  one_nat_gateway_per_az = var.one_nat_gateway_per_az
  single_nat_gateway     = false

  enable_dns_support   = true
  enable_dns_hostnames = true

  map_public_ip_on_launch = true

  public_subnet_tags = {
    "kubernetes.io/role/elb"                    = "1"
    "kubernetes.io/cluster/${var.cluster_name}" = "shared"
  }

  private_subnet_tags = {
    "kubernetes.io/role/internal-elb"           = "1"
    "kubernetes.io/cluster/${var.cluster_name}" = "shared"
  }

  public_route_table_tags = merge(var.tags, {
    Name = "${var.name_prefix}-rt-public"
  })

  private_route_table_tags = merge(var.tags, {
    Name = "${var.name_prefix}-rt-private"
  })

  database_route_table_tags = merge(var.tags, {
    Name = "${var.name_prefix}-rt-data"
  })

  igw_tags = merge(var.tags, {
    Name = "${var.name_prefix}-ig"
  })

  nat_gateway_tags = merge(var.tags, {
    Name = "${var.name_prefix}-nat"
  })

  tags = var.tags
}
