##############################################################
#       AWS Load Balancer Controller add-on
##############################################################

resource "helm_release" "alb-controller" {
  name       = "aws-load-balancer-controller"
  repository = "https://aws.github.io/eks-charts"
  chart      = "aws-load-balancer-controller"
  namespace  = "kube-system"
  version    = "1.7.1"


  set {
    name  = "region"
    value = data.aws_region.current.id
  }

  set {
    name  = "vpcId"
    value = data.aws_vpc.this.id
  }

    set {
    name  = "rbac.create"
    value = "true"
  }

  set {
    name  = "serviceAccount.create"
    value = "true"
  }

  set {
    name  = "serviceAccount.annotations.eks\\.amazonaws\\.com/role-arn"
    value = module.aws_lbc_addon_sa_role.iam_role_arn
  }


  set {
    name  = "nodeSelector.cloud-pipeline/node-group-type"
    value = "system"
  }

  set {
    name  = "clusterName"
    value = module.eks.cluster_name
  }

  set {
    name  = "enableServiceMutatorWebhook"
    value = "false"
  }
}
