# Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

locals {

  tags = merge({
    Environment    = var.deployment_env
    Deployment     = var.deployment_name
    },
    var.additional_tags
  )

  eks_system_node_labels = {
    "cloud-pipeline/node-group-type" : "system"
  }

  resource_name_prefix = "${var.deployment_name}-${var.deployment_env}"
  rds_resource_name_prefix = lower(replace("${var.deployment_name}-${var.deployment_env}", "_", "-"))

  cluster_name         = "${local.resource_name_prefix}-eks-cluster"
  efs_name             = "${local.resource_name_prefix}-efs-file-system"

  system_node_groups = {
    for subnet_id in var.eks_system_node_group_subnet_ids : "system_${subnet_id}" => {
      name = "system-${subnet_id}-ng"

      subnet_ids     = [subnet_id]
      instance_types = [var.eks_system_node_group_instance_type]
      block_device_mappings = {
        xvda = {
          device_name = "/dev/xvda"
          ebs = {
            volume_size           = var.eks_system_node_group_volume_size
            volume_type           = var.eks_system_node_group_volume_type
            encrypted             = true
            delete_on_termination = true
          }
        }
      }

      labels = merge(
        local.eks_system_node_labels,
        {
          "cloud-pipeline/node-group-subnet" : subnet_id
        }
      )

      min_size     = var.eks_system_node_group_size
      max_size     = var.eks_system_node_group_size
      desired_size = var.eks_system_node_group_size
    }
  }

  sso_additional_role_mapping = [
    for role_mapping in var.eks_additional_role_mapping : {
      rolearn  = role_mapping.iam_role_arn
      username = role_mapping.eks_role_name
      groups   = role_mapping.eks_groups
    }
  ]

  vpc_cni_addon_config = var.eks_vpc_cni_driver_custom_config.enable ? {
    vpc-cni = {
      most_recent    = true
      before_compute = true
      configuration_values = jsonencode({
        env = {
          # Reference docs https://docs.aws.amazon.com/eks/latest/userguide/cni-increase-ip-addresses.html
          WARM_ENI_TARGET    = tostring(var.eks_vpc_cni_driver_custom_config.warm_eni_target)
          WARM_IP_TARGET     = tostring(var.eks_vpc_cni_driver_custom_config.warm_ip_target)
          MINIMUM_IP_TARGET  = tostring(var.eks_vpc_cni_driver_custom_config.min_ip_target)
        }
        # https://docs.aws.amazon.com/eks/latest/userguide/windows-support.html
        enableWindowsIpam = "true"
      })
    }
  } : {}

  cloud_pipeline_db_configuration = var.create_cloud_pipeline_db_configuration && var.deploy_rds ? var.cloud_pipeline_db_configuration : {}

  pipeline_db_pass = var.cloud_pipeline_db_configuration["pipeline"].password != null ? var.cloud_pipeline_db_configuration["pipeline"].password : nonsensitive(random_password.this["pipeline"].result)
  clair_db_pass    = var.cloud_pipeline_db_configuration["clair"].password != null ? var.cloud_pipeline_db_configuration["clair"].password : nonsensitive(random_password.this["clair"].result)

  cp_filesystem_id = var.deploy_filesystem_type == "efs" ? module.cp_system_efs.id : try(aws_fsx_lustre_file_system.fsx[0].id, "<efs-id>")
  cp_filesystem_exec_role = var.deploy_filesystem_type == "efs" ? module.efs_csi_irsa.iam_role_arn : module.fsx_csi_irsa.iam_role_arn
  cp_filesystem_mountname = var.deploy_filesystem_type == "fsx" ? aws_fsx_lustre_file_system.fsx[0].mount_name : ""

  az_to_subnet = {for s in data.aws_subnet.info : s.availability_zone => s.id}
  
  deploy_idp = var.cp_idp_host != null ? [
    "-s cp-idp",
    "-env CP_IDP_EXTERNAL_HOST=\"${var.cp_idp_host}\"",
    "-env CP_IDP_INTERNAL_HOST=\"${var.cp_idp_host}\"",
    "-env CP_IDP_EXTERNAL_PORT=443",
    "-env CP_IDP_INTERNAL_PORT=443",
  ] : [
    "-env CP_API_SRV_SSO_ENDPOINT_ID=\"https://${var.cp_api_srv_host}/pipeline\"",
    "-env CP_API_SRV_SAML_USER_ATTRIBUTES=\"${var.cp_api_srv_saml_user_attr}\""
  ]

  aws_omics_specific_envs = var.enable_aws_omics_integration ? [
    "-env CP_PREF_AWS_OMICS_SERVICE_ROLE=${aws_iam_role.cp_omics_service[0].arn}",
    "-env CP_PREF_AWS_OMICS_ECR_REGISTRY=${data.aws_caller_identity.current.account_id}.dkr.ecr.${data.aws_region.current.name}.amazonaws.com"
  ] : []

  cp_edge_elb_sgs = join(
    ",",
    concat(
      var.external_access_security_group_ids,
      [module.internal_cluster_access_sg.security_group_id]
    )
  )

  cp_edge_elb_envs = concat(
    [
      "-env CP_EDGE_AWS_ELB_SCHEME=\"${var.cp_edge_elb_schema}\"",
      "-env CP_EDGE_AWS_ELB_SUBNETS=\"${var.cp_edge_elb_subnet}\"",
      "-env CP_EDGE_AWS_ELB_SG=\"${local.cp_edge_elb_sgs}\"",
    ],
    var.cp_edge_elb_schema == "internet-facing"
      ? ["-env CP_EDGE_AWS_ELB_EIPALLOCS=\"${var.cp_edge_elb_ip}\"", "-env CP_EDGE_KUBE_SERVICES_TYPE=elb"]
      : ["-env CP_EDGE_AWS_ELB_PRIVATE_IPS=\"${var.cp_edge_elb_ip}\"", "-env CP_EDGE_KUBE_SERVICES_TYPE=elb-internal"]
  )

  default_admin_name = var.cp_default_admin_name != null ? ["-env CP_DEFAULT_ADMIN_NAME=${var.cp_default_admin_name}"] : []

  deploy_script = join(" \\\n", concat([
    "./pipectl install",
    "-dt aws-native",
    "-jc",
    "-env CP_MAIN_SERVICE_ROLE=\"${module.cp_irsa.iam_role_arn}\"",
    "-env CP_CSI_DRIVER_TYPE=\"${var.deploy_filesystem_type}\"",
    "-env CP_SYSTEM_FILESYSTEM_ID=\"${local.cp_filesystem_id}\"",
    "-env CP_SYSTEM_FILESYSTEM_MOUNTNAME=\"${local.cp_filesystem_mountname}\"",
    "-env CP_CSI_EXECUTION_ROLE=\"${local.cp_filesystem_exec_role}\"",
    "-env CP_DOCKER_DIST_SRV=\"quay.io/\"",
    "-env CP_AWS_KMS_ARN=\"${module.kms.key_arn}\"",
    "-env CP_PREF_CLUSTER_SSH_KEY_NAME=\"${module.ssh_rsa_key_pair.key_pair_name}\"",
    "-env CP_PREF_CLUSTER_INSTANCE_SECURITY_GROUPS=\"${module.internal_cluster_access_sg.security_group_id}\"",
    "-env CP_PREF_STORAGE_TEMP_CREDENTIALS_ROLE=\"${aws_iam_role.cp_s3_via_sts.arn}\"",
    "-env CP_CLUSTER_SSH_KEY=\"/opt/root/ssh/ssh-key.pem\"",
    "-env CP_DOCKER_STORAGE_TYPE=\"obj\"",
    "-env CP_DOCKER_STORAGE_CONTAINER=\"${module.s3_docker.s3_bucket_id}\"",
    "-env CP_DEPLOYMENT_ID=\"${var.cp_deployment_id}\"",
    "-env CP_PREF_UI_PIPELINE_DEPLOYMENT_NAME=\"${var.cp_deployment_id}\"",
    "-env CP_CLOUD_REGION_ID=\"${data.aws_region.current.name}\"",
    "-env CP_KUBE_CLUSTER_NAME=\"${module.eks.cluster_name}\"",
    "-env CP_KUBE_EXTERNAL_HOST=\"${module.eks.cluster_endpoint}\"",
    "-env CP_KUBE_SERVICES_TYPE=\"ingress\"",
    "--external-host-dns",
    "-env PSG_HOST=\"${module.cp_rds.db_instance_address}\"",
    "-env PSG_PASS=\"${local.pipeline_db_pass}\"",
    "-env PSG_CONNECT_PARAMS=\"${var.deploy_rds && var.rds_force_ssl == 1 ? "?ssl=true&sslfactory=org.postgresql.ssl.NonValidatingFactory" : ""}\"",
    "-s cp-api-srv",
    "-env CP_API_SRV_EXTERNAL_PORT=443",
    "-env CP_API_SRV_INTERNAL_PORT=443",
    "-env CP_API_SRV_EXTERNAL_HOST=\"${var.cp_api_srv_host}\"",
    "-env CP_API_SRV_INTERNAL_HOST=\"${var.cp_api_srv_host}\"",
    "-env CP_API_SRV_IDP_CERT_PATH=\"/opt/idp/pki\"",
    "-env CP_PREF_STORAGE_SYSTEM_STORAGE_NAME=\"${module.s3_etc.s3_bucket_id}\"",
    "-env CP_API_SRV_SSO_BINDING=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST\"",
    "-env CP_API_SRV_SAML_ALLOW_ANONYMOUS_USER=\"true\"",
    "-env CP_API_SRV_SAML_AUTO_USER_CREATE=\"EXPLICIT\"",
    "-env CP_API_SRV_SAML_GROUPS_ATTRIBUTE_NAME=\"Group\"",
    "-env CP_HA_DEPLOY_ENABLED=\"true\"",
    "-s cp-docker-registry",
    "-env CP_DOCKER_EXTERNAL_PORT=443",
    "-env CP_DOCKER_INTERNAL_PORT=443",
    "-env CP_DOCKER_EXTERNAL_HOST=\"${var.cp_docker_host}\"",
    "-env CP_DOCKER_INTERNAL_HOST=\"${var.cp_docker_host}\"",
    "-env CP_DOCKER_STORAGE_ROOT_DIR=\"/docker-pub/\"",
    "-s cp-edge",
    "-env CP_EDGE_EXTERNAL_PORT=443",
    "-env CP_EDGE_INTERNAL_PORT=443",
    "-env CP_EDGE_EXTERNAL_HOST=\"${var.cp_edge_host}\"",
    "-env CP_EDGE_INTERNAL_HOST=\"${var.cp_edge_host}\"",
    "-env CP_EDGE_WEB_CLIENT_MAX_SIZE=0",
    "-s cp-clair",
    "-env CP_CLAIR_DATABASE_HOST=\"${module.cp_rds.db_instance_address}\"",
    "-env CP_CLAIR_DATABASE_PASSWORD=\"${local.clair_db_pass}\"",
    "-env CP_CLAIR_DATABASE_SSL_MODE=\"${var.deploy_rds && var.rds_force_ssl == 1 ? "require" : "disable"}\"",
    "-s cp-docker-comp",
    "-env CP_DOCKER_COMP_WORKING_DIR=\"/cloud-pipeline/docker-comp/wd\"",
    "-s cp-search",
    "-s cp-dav",
    "-env CP_DAV_AUTH_URL_PATH=\"webdav/auth-sso\"",
    "-env CP_DAV_MOUNT_POINT=\"/dav-mount\"",
    "-env CP_DAV_SERVE_DIR=\"/dav-serve\"",
    "-env CP_DAV_URL_PATH=\"webdav\"",
    "-s cp-gitlab-db",
    "-env GITLAB_DATABASE_VERSION=\"16.4\"",
    "-s cp-git",
    "-env CP_GITLAB_VERSION=17",
    "-env CP_GITLAB_SESSION_API_DISABLE=\"true\"",
    "-env CP_GITLAB_API_VERSION=v4",
    "-env CP_GITLAB_EXTERNAL_PORT=443",
    "-env CP_GITLAB_INTERNAL_PORT=443",
    "-env CP_GITLAB_EXTERNAL_HOST=\"${var.cp_gitlab_host}\"",
    "-env CP_GITLAB_INTERNAL_HOST=\"${var.cp_gitlab_host}\"",
    "-env CP_GITLAB_EXTERNAL_URL=\"https://${var.cp_gitlab_host}\"",
    "-env CP_GITLAB_IDP_CERT_PATH=\"/opt/idp/pki\"",
    "-s cp-git-sync",
    "-s cp-billing-srv",
    "-env CP_BILLING_DISABLE_GS=\"true\"",
    "-env CP_BILLING_DISABLE_AZURE_BLOB=\"true\"",
    "-env CP_BILLING_CENTER_KEY=\"billing-group\"",
    "-env CP_CLOUD_NETWORK_CONFIG_FILE=$${CP_CLUSTER_NETWORKS_CONFIG_JSON}"
    ],
    local.deploy_idp,
    local.aws_omics_specific_envs,
    local.cp_edge_elb_envs,
    local.default_admin_name
  ))
  
}

