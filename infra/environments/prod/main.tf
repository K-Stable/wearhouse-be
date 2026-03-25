locals {
  active_vpc_id = var.manage_network ? module.network[0].vpc_id : var.existing_vpc_id

  active_public_subnet_ids  = var.manage_network ? module.network[0].public_subnet_ids : var.existing_public_subnet_ids
  active_private_subnet_ids = var.manage_network ? module.network[0].private_subnet_ids : var.existing_private_subnet_ids
  active_data_subnet_ids = var.manage_network ? module.network[0].database_subnet_ids : (
    length(var.existing_data_subnet_ids) > 0 ? var.existing_data_subnet_ids : var.existing_private_subnet_ids
  )

  node_security_group_ids = var.manage_eks ? [module.eks[0].node_security_group_id] : []
}

module "network" {
  count  = var.manage_network ? 1 : 0
  source = "../../modules/network"

  name_prefix      = var.project_name
  cluster_name     = var.cluster_name
  cidr             = var.vpc_cidr
  azs              = var.azs
  public_subnets   = var.public_subnet_cidrs
  private_subnets  = var.private_subnet_cidrs
  database_subnets = var.data_subnet_cidrs

  enable_nat_gateway     = true
  one_nat_gateway_per_az = true
  tags                   = local.common_tags
}

module "eks" {
  count  = var.manage_eks ? 1 : 0
  source = "../../modules/eks"

  cluster_name          = var.cluster_name
  vpc_id                = local.active_vpc_id
  subnet_ids            = local.active_private_subnet_ids
  node_group_subnet_ids = local.active_private_subnet_ids

  node_instance_types = var.node_instance_types
  node_desired_size   = var.node_desired_size
  node_min_size       = var.node_min_size
  node_max_size       = var.node_max_size

  tags = local.common_tags
}

module "ecr" {
  source = "../../modules/ecr"

  create_repositories = var.create_ecr_repositories
  repository_names    = [for service in var.service_names : "${var.project_name}/${service}"]
  tags                = local.common_tags
}

resource "aws_security_group" "rds" {
  name        = "${var.project_name}-rds-sg"
  description = "RDS access from EKS nodes"
  vpc_id      = local.active_vpc_id

  ingress {
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    cidr_blocks     = var.manage_eks ? [] : [var.existing_vpc_cidr]
    security_groups = local.node_security_group_ids
    description     = "MySQL from EKS nodes or VPC"
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.common_tags, { Name = "${var.project_name}-rds-sg" })
}

resource "aws_security_group" "msk" {
  name        = "${var.project_name}-msk-sg"
  description = "MSK access from EKS nodes"
  vpc_id      = local.active_vpc_id

  ingress {
    from_port       = 9092
    to_port         = 9098
    protocol        = "tcp"
    cidr_blocks     = var.manage_eks ? [] : [var.existing_vpc_cidr]
    security_groups = local.node_security_group_ids
    description     = "Kafka from EKS nodes or VPC"
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.common_tags, { Name = "${var.project_name}-msk-sg" })
}

module "rds" {
  source = "../../modules/rds"

  identifier             = "${var.project_name}-mysql"
  db_name                = var.db_name
  username               = var.db_username
  password               = var.db_password
  instance_class         = var.db_instance_class
  subnet_ids             = local.active_data_subnet_ids
  vpc_security_group_ids = [aws_security_group.rds.id]

  deletion_protection = false
  skip_final_snapshot = true
  tags                = local.common_tags
}

module "msk" {
  source = "../../modules/msk"

  cluster_name       = "${var.project_name}-kafka"
  subnet_ids         = local.active_private_subnet_ids
  security_group_ids = [aws_security_group.msk.id]

  broker_instance_type = var.kafka_instance_type
  ebs_volume_size      = var.kafka_volume_size
  tags                 = local.common_tags
}

module "ssm" {
  source = "../../modules/ssm"

  secure_parameters = local.ssm_secure_full
  plain_parameters  = local.ssm_plain_full
  tags              = local.common_tags
}

data "aws_iam_openid_connect_provider" "existing" {
  count = var.manage_addons && !var.manage_eks ? 1 : 0
  url   = data.aws_eks_cluster.this.identity[0].oidc[0].issuer
}

module "alb_irsa_role" {
  count   = var.manage_addons ? 1 : 0
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "~> 5.44"

  role_name                              = "${var.project_name}-alb-controller-irsa"
  attach_load_balancer_controller_policy = true

  oidc_providers = {
    main = {
      provider_arn = var.manage_eks ? module.eks[0].oidc_provider_arn : data.aws_iam_openid_connect_provider.existing[0].arn

      namespace_service_accounts = ["kube-system:aws-load-balancer-controller"]
    }
  }

  tags = local.common_tags
}

resource "helm_release" "metrics_server" {
  count = var.manage_addons && var.manage_metrics_server ? 1 : 0

  name       = "metrics-server"
  namespace  = "kube-system"
  repository = "https://kubernetes-sigs.github.io/metrics-server/"
  chart      = "metrics-server"
}

resource "helm_release" "aws_load_balancer_controller" {
  count = var.manage_addons ? 1 : 0

  name       = "aws-load-balancer-controller"
  namespace  = "kube-system"
  repository = "https://aws.github.io/eks-charts"
  chart      = "aws-load-balancer-controller"

  set {
    name  = "clusterName"
    value = var.cluster_name
  }

  set {
    name  = "region"
    value = var.region
  }

  set {
    name  = "vpcId"
    value = local.active_vpc_id
  }

  set {
    name  = "serviceAccount.create"
    value = "true"
  }

  set {
    name  = "serviceAccount.name"
    value = "aws-load-balancer-controller"
  }

  set {
    name  = "serviceAccount.annotations.eks\\.amazonaws\\.com/role-arn"
    value = module.alb_irsa_role[count.index].iam_role_arn
  }
}

resource "helm_release" "argocd" {
  count = var.manage_addons ? 1 : 0

  name             = "argocd"
  namespace        = "argocd"
  repository       = "https://argoproj.github.io/argo-helm"
  chart            = "argo-cd"
  create_namespace = true

  set {
    name  = "server.service.type"
    value = "ClusterIP"
  }

  set {
    name  = "configs.params.server\\.insecure"
    value = "true"
  }
}
