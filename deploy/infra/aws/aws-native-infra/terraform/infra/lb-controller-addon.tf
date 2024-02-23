##############################################################
#       AWS Load Balancer Controller add-on
##############################################################

provider "helm" {
  kubernetes {
    host                   = module.eks.cluster_endpoint
    token                  = module.eks.token
    cluster_ca_certificate = base64decode(module.eks.cluster_certificate_authority_data)
  }
}

module "lb_controller_addon" {
  source  = "lablabs/eks-load-balancer-controller/aws"
  version = "1.3.0"
  enabled           = true
  argo_enabled      = false
  argo_helm_enabled = false
  irsa_role_name_prefix = "${local.resource_name_prefix}EKS_LB_Controller"

  cluster_name                     = module.eks.cluster_id
  cluster_identity_oidc_issuer     = module.eks.oidc_provider
  cluster_identity_oidc_issuer_arn = module.eks.oidc_provider_arn

  helm_release_name = "aws-load-balancer-controller"
  namespace         = "kube-system"

  values = yamlencode({
    "podLabels" : {
      "app" : "cp-lbc-addon"
    }
  })

  helm_timeout = 240
  helm_wait    = true
}