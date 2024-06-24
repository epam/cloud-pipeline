resource "aws_route53_record" "cp-domain" {
  zone_id = var.cp_zone_id
  name    = var.cp_domain_records
  type    = "A"
  ttl     = "300"
  records = [aws_eip.cp_eip_core.public_ip]
}

resource "aws_route53_record" "cp-edge" {
  zone_id = var.cp_zone_id
  name    = "edge.${var.cp_domain_records}"
  type    = "CNAME"
  ttl     = "5"

  records = ["${var.cp_domain_records}.${var.cp_domain_name}."]
}

resource "aws_route53_record" "cp-git" {
  zone_id = var.cp_zone_id
  name    = "git.${var.cp_domain_records}"
  type    = "CNAME"
  ttl     = "5"

  records = ["${var.cp_domain_records}.${var.cp_domain_name}."]
}

resource "aws_route53_record" "cp-docker" {
  zone_id = var.cp_zone_id
  name    = "docker.${var.cp_domain_records}"
  type    = "CNAME"
  ttl     = "5"

  records = ["${var.cp_domain_records}.${var.cp_domain_name}."]
}

resource "aws_route53_record" "cp-auth" {
  zone_id = var.cp_zone_id
  name    = "auth.${var.cp_domain_records}"
  type    = "CNAME"
  ttl     = "5"

  records = ["${var.cp_domain_records}.${var.cp_domain_name}."]
}

resource "aws_route53_record" "cp-rstudio" {
  zone_id = var.cp_zone_id
  name    = "rstudio.${var.cp_domain_records}"
  type    = "CNAME"
  ttl     = "5"

  records = ["${var.cp_domain_records}.${var.cp_domain_name}."]
}

resource "aws_route53_record" "cp-shiny" {
  zone_id = var.cp_zone_id
  name    = "shiny.${var.cp_domain_records}"
  type    = "CNAME"
  ttl     = "5"

  records = ["${var.cp_domain_records}.${var.cp_domain_name}."]
}
