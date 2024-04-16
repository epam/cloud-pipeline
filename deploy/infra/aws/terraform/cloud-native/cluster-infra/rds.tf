module "cp_rds" {
  source  = "terraform-aws-modules/rds/aws"
  version = "6.4.0"


  identifier = "${local.resource_name_prefix}-rds"

  create_db_instance        = var.deploy_rds
  create_db_option_group    = false
  skip_final_snapshot       = true

  # All available versions: https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/CHAP_PostgreSQL.html#PostgreSQL.Concepts
  engine         = "postgres"
  engine_version = "12.18"
  family         = "postgres12" # DB parameter group
  instance_class = var.rds_instance_type


  allocated_storage = var.rds_storage_size

  db_subnet_group_name   = aws_db_subnet_group.rds_subnet_group.name
  vpc_security_group_ids = [module.internal_cluster_access_sg.security_group_id]

  db_name                     = var.rds_default_db_name
  username                    = var.rds_root_username
  password                    = var.rds_root_password != null ? var.rds_root_password : try(random_password.rds_default_db_password[0].result, null)
  manage_master_user_password = false
  port                        = var.rds_db_port

  parameters = [
    {
      name  = "rds.force_ssl"
      value = 1
    }
  ]


  tags = local.tags

}

resource "aws_db_subnet_group" "rds_subnet_group" {
  name       = "${local.resource_name_prefix}-db-subnet-group"
  subnet_ids = var.subnet_ids

  tags = local.tags
}

resource "random_password" "rds_default_db_password" {
  count            = var.deploy_rds && var.rds_root_password == null ? 1 : 0
  length           = 16
  special          = true
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

resource "aws_secretsmanager_secret" "rds_root_secret" {
  count                   = var.deploy_rds ? 1 : 0
  name                    = "rds/${local.resource_name_prefix}_${var.rds_default_db_name}_db/${var.rds_root_username}"
  recovery_window_in_days = 0 // Overriding the default recovery window of 30 days
}

resource "aws_secretsmanager_secret_version" "rds_root_secret" {
  count         = var.deploy_rds ? 1 : 0
  secret_id     = aws_secretsmanager_secret.rds_root_secret[0].id
  secret_string = var.rds_root_password != null ? var.rds_root_password : random_password.rds_default_db_password[0].result
}

##########################################################################
#           Additional databases and user-owner for them
##########################################################################

resource "random_password" "this" {
  for_each         = { for db in local.cloud_pipeline_db_configuration : db.username => db if db.password == null }
  length           = 16
  special          = true
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

resource "aws_secretsmanager_secret" "this" {
  for_each                = { for db in local.cloud_pipeline_db_configuration : db.username => db if db.password == null }
  name                    = "rds/${local.resource_name_prefix}_${each.value.database}_db/${each.value.username}"
  description             = "Password for ${each.value.username} to RDS Database ${each.value.database}"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "this" {
  for_each = { for db in local.cloud_pipeline_db_configuration : db.username => db if db.password == null }

  secret_id     = aws_secretsmanager_secret.this[each.key].arn
  secret_string = random_password.this[each.key].result
}

resource "postgresql_role" "this" {
  for_each        = { for db in local.cloud_pipeline_db_configuration : db.username => db }
  name            = each.value.username
  password        = each.value.password != null ? each.value.password : random_password.this[each.key].result
  login           = true

  depends_on = [aws_secretsmanager_secret.rds_root_secret, aws_secretsmanager_secret_version.rds_root_secret, module.internal_cluster_access_sg]
}

resource "postgresql_database" "this" {
  for_each = { for db in local.cloud_pipeline_db_configuration : db.username => db }
  name     = each.value.database
  owner    = each.value.username

  depends_on = [aws_secretsmanager_secret.rds_root_secret, aws_secretsmanager_secret_version.rds_root_secret, postgresql_role.this[0]]
}





