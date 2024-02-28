locals {
  tags = merge({
    Environment = var.env
    Project     = var.project_name
    },
    var.additional_tags
  )
  resource_name_prefix = "${var.project_name}-${var.env}"
}
