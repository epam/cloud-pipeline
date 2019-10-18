# Build AMI (AWS and Azure example)
```bash
# AWS-specific parameters
export AWS_ACCESS_KEY_ID=
export AWS_SECRET_ACCESS_KEY=

# Azure-specific parameters
export CP_AZURE_AUTH_LOCATION=
export CP_AZURE_RESOURCE_GROUP=

# Docker-specific parameters
export CP_DOCKER_DIST_USER=                                         # Optional, if non-default (lifescience/cloud-pipeline) dockerhub images will be used
export CP_DOCKER_DIST_PASS=                                         # Optional, if non-default (lifescience/cloud-pipeline) dockerhub images will be used
export CP_API_DIST_URL=                                             # Specify API distribution tarball URI. If not set - latest version will be used from https://s3.amazonaws.com/cloud-pipeline-oss-builds/builds/latest/develop/cloud-pipeline.latest.tgz

bash build.sh -aws eu-central-1,us-east-1 \                         # List of regions to build VM images in AWS. -im shall be set to "rebuild" to build images from scratch
              -az westeurope,centralus \                            # Same as -aws, but Azure environment
              -im ${PATH_TO_VM_IMAGES_MANIFEST} \                   # OR a path to a prebuilt VM images manifest. If both are not set - default manifest will be used (https://s3.amazonaws.com/cloud-pipeline-oss-builds/manifests/cloud-images-manifest.txt)
              -p ../workflows/pipe-templates/__SYSTEM/data_loader \ # Path to any packages that shall be included into the pipectl distr
              -p ../e2e/prerequisites \                             # E.g.: system data transfer pipeline or a list of users to regsiter by default
              -p ../workflow/pipe-demo \                            # Path to the demo pipelines directory. If it is specifed - pipelines will be registered, as defined in the corresponding spec.json
              -t \                                                  # Whether to include test docker images
              -v 0.15                                               # Cloud Pipeline distribution version (used to tag docker images)
```



# Run pipectl
```bash
~/.pipe/pipectl install \
                # Docker distribution credentials
                -env CP_DOCKER_DIST_USER= \
                -env CP_DOCKER_DIST_PASS= \
                
                # Cluster SSH and network access
                -env CP_CLUSTER_SSH_PUB= \                          # Path to the SSH public key when deploying to Azure and GCP. For AWS use CP_PREF_CLUSTER_SSH_KEY_NAME
                -env CP_CLUSTER_SSH_KEY= \                          # Path to the SSH private key - required when deploying to all Clouds
                -env CP_PREF_CLUSTER_SSH_KEY_NAME= \                # Name of the SSH public key in AWS. Used only for AWS deployment, for Azure and GCP - use CP_CLUSTER_SSH_PUB
                -env CP_PREF_CLUSTER_INSTANCE_SECURITY_GROUPS= \    # 
                -env CP_PREF_CLUSTER_INSTANCE_IMAGE \               # Which VM image to use as a default for CPU-only workloads (if a VM manifest  for a current cloud provider exists - this is optional)
                -env CP_PREF_CLUSTER_INSTANCE_IMAGE_GPU \           # Which VM image to use as a default for GPU workloads (if a VM manifest for a current cloud provider exists - this is optional)

                # Cloud Provider credentials
                ## Common
                -env CP_CLOUD_CREDENTIALS_FILE= \                   # Cloud credentials can be specified as a file for any cloud provider (the only available option for Azure and GCP)
                ## AWS
                -env CP_AWS_ACCESS_KEY_ID= \                        # For AWS key id can be specified via environment variables
                -env CP_AWS_SECRET_ACCESS_KEY= \                    # For AWS key secret can be specified via environment variables
                -env CP_AWS_KMS_ARN= \
                -env CP_PREF_STORAGE_TEMP_CREDENTIALS_ROLE= \
                -env CP_DOCKER_STORAGE_ROOT_DIR= \                  # Root directory within a $CP_DOCKER_STORAGE_CONTAINER, used to store images blobs. If not set - "cloud-pipeline-${CP_DEPLOYMENT_ID}" will be used
                ## Azure
                -env CP_AZURE_STORAGE_ACCOUNT= \                    # Default storage account name, that will be used to manage BLOB/FS storages and persist docker images (if CP_DOCKER_STORAGE_TYPE=obj)
                -env CP_AZURE_STORAGE_KEY= \                        # Key for the default storage account (CP_AZURE_STORAGE_ACCOUNT)
                -env CP_AZURE_DEFAULT_RESOURCE_GROUP= \             # Which Azure resource group will be used by default
                -env CP_AZURE_OFFER_DURABLE_ID=\                    # 
                -env CP_AZURE_SUBSCRIPTION_ID=\                     # 

                # Core API
                -env CP_API_SRV_SAML_ID_TRAIL= \                    # SAML partner ID will be constructed as {CP_API_SRV_EXTERNAL_HOST}:{CP_API_SRV_EXTERNAL_PORT} and this parameter added in the end (default: /pipeline/)
                -env CP_API_SRV_SAML_AUTO_USER_CREATE= \            # Whether to register all users that have passed SAML authentication. Such users will be granted basic "ROLE_USER" permissions. The following value are available: AUTO (creates a new user if not exists), EXPLICIT (requires users pre-registration (performed by any admin), EXPLICIT_GROUP (requires specific groups pre-registration. If user's SAML groups have no intersections with registered groups the authentication will fail)
                -env CP_API_SRV_IDP_CERT_PATH= \                    # Allows to set the path to the directory containing IdP's signing certificate (idp-public-cert.pem). If not set - $CP_IDP_CERT_DIR will be used. This is useful if the IdP provides different signing certificate for different services
                -env CP_PREF_CLUSTER_CADVISOR_DISABLE_PROXY= \      # Disables the proxy settings when API communicates to the cAdvisor service within worker nodes (Default: true)

                # GitLab
                -env CP_GITLAB_SSO_TARGET_URL= \                    # Sets idp_sso_target_url value of the gitlab.rb, if not defined - it will be constructed as "https://${CP_IDP_EXTERNAL_HOST}:${CP_IDP_EXTERNAL_PORT}${CP_GITLAB_SSO_TARGET_URL_TRAIL}"
                -env CP_GITLAB_SLO_TARGET_URL= \                    # Sets idp_slo_target_url value of the gitlab.rb, if not defined - it will be constructed as "https://${CP_IDP_EXTERNAL_HOST}:${CP_IDP_EXTERNAL_PORT}${CP_GITLAB_SLO_TARGET_URL_TRAIL}"
                -env CP_GITLAB_SSO_TARGET_URL_TRAIL= \              # Allows to add a trailing part to the idp_sso_target_url (default: "/saml/sso")
                -env CP_GITLAB_SLO_TARGET_URL_TRAIL= \              # Allows to add a trailing part to the idp_slo_target_url (default: "/saml/sso")
                -env CP_GITLAB_IDP_CERT_PATH= \                     # Allows to set the path to the directory containing IdP's signing certificate (idp-public-cert.pem). If not set - $CP_IDP_CERT_DIR will be used. This is useful if the IdP provides different signing certificate for different services
                -env CP_GITLAB_EXTERNAL_URL= \                      # Allows to specify a custom value for the gitlab's "external_url". This is used as a base URL for the repositories clone URLs. This value does not affect the gitlab's listen port. It will listen on $CP_GITLAB_INTERNAL_PORT(Default: https://${CP_GITLAB_INTERNAL_HOST}:${CP_GITLAB_INTERNAL_PORT})

                # SMTP notifications parameters
                -env CP_NOTIFIER_SMTP_SERVER_HOST= \
                -env CP_NOTIFIER_SMTP_SERVER_PORT= \
                -env CP_NOTIFIER_SMTP_FROM= \
                -env CP_NOTIFIER_SMTP_USER= \
                -env CP_NOTIFIER_SMTP_PASS= \

                # Docker registry
                -env CP_DOCKER_STORAGE_TYPE= \                      # Specify "obj" to use object storage backend for the docker registry (S3/Azure storage/GCS), otherwise - local filesystem will be used
                -env CP_DOCKER_STORAGE_CONTAINER="" \               # If CP_DOCKER_STORAGE_TYPE is set to "obj" - specify name of the object storage (bucket/container name)

                # Default administrator
                -env CP_DEFAULT_ADMIN_NAME= \
                -env CP_DEFAULT_ADMIN_PASS= \
                -env CP_DEFAULT_ADMIN_EMAIL= \
                
                # VM Monitor
                -env CP_VM_MONITOR_HOUR_INTERVAL= \                 # Specify interval in hours between VM Monitor checks. Value 1 (default) means that VM Monitor will check VMs each hour       
                -env CP_VM_MONITOR_INSTANCE_TAG_NAME= \             # VM Monitor will check status only of nodes labeled by this tag and value CP_VM_MONITOR_INSTANCE_TAG_VALUE 
                -env CP_VM_MONITOR_INSTANCE_TAG_VALUE= \            # VM Monitor will check status only of nodes labeled by tag CP_VM_MONITOR_INSTANCE_TAG_NAME and this value 
                -env CP_VM_MONITOR_TO_USER= \                       # Username that shall by notified when VM Monitor detects invalid VM state
                -env CP_VM_MONITOR_CC_USERS= \                      # Usernames that shall by additionaly notified (cc) when VM Monitor detects invalid VM state

                # Share Service
                -env CP_SHARE_SRV_SAML_ID_TRAIL = \                 # SAML partner ID will for Share Service be constructed as {CP_SHARE_SRV_EXTERNAL_HOST}:{CP_SHARE_SRV_EXTERNAL_PORT} and this parameter 
                -env CP_SHARE_SRV_SAMPLE_ROLE_CLAIMS = \            # SAML claims that shall be used as ROLEs while parsing user info receinved from IDP
                -env CP_SHARE_SRV_IDP_CERT_PATH= \                    # Allows to set the path to the directory containing IdP's signing certificate (idp-public-cert.pem). If not set - $CP_IDP_CERT_DIR will be used. This is useful if the IdP provides different signing certificate for different services

                # EDGE Service
                -env CP_EDGE_WEB_CLIENT_MAX_SIZE = \                # Sets the maximum file (request) size to be uploaded via the EDGE service, to remove the limit - set it to 0 (default: 500M)

                # Pipectl options
                -m|--install-kube-master \                          # Install kuberneters master
                -d|--docker \                                       # Limit images to be pushed during deployment
                -id|--deployment-id \                               # Specify unique ID of the deployment. It will be used to name cloud entities (e.g. path within a docker registry object container). If not set - random 10-char string will be generated
                -s|--service \                                      # Limit services to be installed (e.g. cp-idp, cp-api-srv, etc.)
                --keep-kubedm-proxies \                             # Allow (http/https/no)_proxy settings to be included in to kube-api manifest by kubeadm. If option is not set - variables will be dropped before the kubeadm init command and then restored

                # Templates customization
                -env CP_PREF_TEMPLATES_DIRECTORY_EXT \              # If defined, shall point to a directory with pipelines templates, which override the defaults (cloud-pipeline/workflows/pipe-templates)
                -env CP_PREF_TEMPLATES_FOLDER_DIRECTORY_EXT \        # If defined, shall point to a directory with folders templates (e.g. "Project" template), which override the defaults (cloud-pipeline/deploy/docker/cp-api-srv/folder-templates)
                -env CP_PREF_TEMPLATES_ERROR_PAGES_DIRECTORY_EXT \  # If defined, shall point to a directory with error pages templates, which override the defaults (cloud-pipeline/deploy/docker/cp-api-srv/error-pages)
                -env CP_ERROR_REDIRECT_URL \                        # Allows to specify a custom value for the Cloud Pipeline main page redirection url to use in the error pages placeholders (default: https://$CP_API_SRV_EXTERNAL_HOST:$CP_API_SRV_EXTERNAL_PORT/pipeline/)
                -env CP_ERROR_PLATFORM_NAME \                       # Allows to specify a custom value for the deployment name to use in the error pages placeholders (default: $CP_PREF_UI_PIPELINE_DEPLOYMENT_NAME from the install-config)
                -env CP_ERROR_SUPPORT_EMAIL \                       # Allows to specify a custom value for the admins' support email to use in the error pages placeholders (default: $CP_DEFAULT_ADMIN_EMAIL from the install-config)

                # Misc
                -env CP_PREF_STORAGE_SYSTEM_STORAGE_NAME= \         # Name of the object storage, that is used to store system-level data (e.g. issues attachments)
                -env CP_CLOUD_REGION_FILE_STORAGE_HOSTS= \
                -env CP_CUSTOM_USERS_SPEC= \                        # Specify json file with the users to be registered during installation (if pipectl was built using -p /e2e/prerequisites/users.json - test users will be created)
                -env CP_COMMON_SSL_SELF_SIGNED= \                   # Use self-signed or CA signed certificates for SSL (true or false, default: false)
                -env CP_KUBE_MASTER_DOCKER_PATH= \                  # Allows to override a location of the folder where docker stores it's data. This is useful when docker generates too much I/O to the OS Disk and shall be pointed to another device mounted to a more custom location. If not defined - docker defaults are used.
                -env CP_KUBE_MASTER_ETCD_HOST_PATH= \               # Allows to override a location of the folder where etcd stores wal/data dirs. This is useful when etcd runs into I/O latency issues and shall be pointed to another device mounted to a more custom location, which leads to the kube control plane failures. If not defined - /var/lib/etcd path will be used
                -env CP_KUBE_MIN_DNS_REPLICAS= \                    # Allows to configure a minimal number of DNS replicas for the cluster (default: 1). DNS will be autoscaled based on the size of a cluster (1 new replica for each 128 cores or 5 nodes)
                -env CP_KUBE_SERVICES_TYPE= \                       # Allows to select a preferred services mode type: "node-port" or "external-ip" (default: "node-port")
                
                # TES Adapter
                -env CP_TES_WHITE_IPV4_CIDR                         # Whitelist ip addresses for access to Cloud Pipeline through TES adapter, if client doesn't have HttpAuthorization in headers or cookies. It supports as IPv4 as Ipv6  
```

# Examples

### AWS - install all services
```
~/.pipe/pipectl   install \
            -env CP_AWS_KMS_ARN="arn:aws:kms:{region}:{account_id}:key/{key_id}" \
            -env CP_AWS_ACCESS_KEY_ID=ABCDEFGHIJKLMNOPQRST \
            -env CP_AWS_SECRET_ACCESS_KEY=abcdefghijklmnopqstuvwxyz1234567890ABCDE \
            -env CP_PREF_CLUSTER_SSH_KEY_NAME={deploykey_name} \
            -env CP_PREF_CLUSTER_INSTANCE_SECURITY_GROUPS="sg-123456789,sg-qwertyui" \
            -env CP_PREF_STORAGE_TEMP_CREDENTIALS_ROLE="arn:aws:iam::{account_id}:role/{role_name}" \
            -env CP_NOTIFIER_SMTP_SERVER_HOST="smtp.server.name" \
            -env CP_NOTIFIER_SMTP_SERVER_PORT={smpt_port} \
            -env CP_NOTIFIER_SMTP_FROM="cloud_pipeline@epam.com" \
            -env CP_NOTIFIER_SMTP_USER="cloud_pipeline@epam.com" \
            -env CP_NOTIFIER_SMTP_PASS="{smtp_password}" \
            -env CP_DEFAULT_ADMIN_EMAIL="pipe_admin@epam.com" \
            -env CP_CLUSTER_SSH_KEY=/path/to/deploykey_name.pem \
            -env CP_PREF_STORAGE_SYSTEM_STORAGE_NAME=pipeline-system \
            -env CP_CLOUD_REGION_FILE_STORAGE_HOSTS="fs-123456789.efs.{region}.amazonaws.com" \
            -env CP_DOCKER_STORAGE_TYPE="obj" \
            -env CP_DOCKER_STORAGE_CONTAINER="{s3_bucket_name}" \
            -env CP_DEPLOYMENT_ID="my_cloud_pipeline" \
            -m
```

### Azure - install all services
```
~/.pipe/pipectl   install \
            -env CP_CLUSTER_SSH_KEY=/path/to/deploykey_name.pem \
            -env CP_CLUSTER_SSH_PUB=/path/to/deploypub_name.pem \
            -env CP_CLOUD_CREDENTIALS_FILE=/path/to/az/credentials \
            -env CP_AZURE_STORAGE_ACCOUNT={storage_account} \
            -env CP_AZURE_STORAGE_KEY=ABCDEFGHI \
            -env CP_AZURE_DEFAULT_RESOURCE_GROUP={resource_group_name} \
            -env CP_AZURE_OFFER_DURABLE_ID=MS-AAA-0000A \
            -env CP_AZURE_SUBSCRIPTION_ID=12345678-1234-1234-1234-12345678 \
            -env CP_PREF_CLUSTER_INSTANCE_SECURITY_GROUPS="sg-123456789,sg-qwertyui" \
            -env CP_NOTIFIER_SMTP_SERVER_HOST="smtp.server.name" \
            -env CP_NOTIFIER_SMTP_SERVER_PORT={smpt_port} \
            -env CP_NOTIFIER_SMTP_FROM="cloud_pipeline@epam.com" \
            -env CP_NOTIFIER_SMTP_USER="cloud_pipeline@epam.com" \
            -env CP_NOTIFIER_SMTP_PASS="{smtp_password}" \
            -env CP_DEFAULT_ADMIN_EMAIL="pipe_admin@epam.com" \
            -env CP_PREF_STORAGE_SYSTEM_STORAGE_NAME=pipeline-system \
            -env CP_CLOUD_REGION_FILE_STORAGE_HOSTS="{fs_name}.file.core.windows.net/{share_name}" \
            -env CP_DOCKER_STORAGE_TYPE="obj" \
            -env CP_DOCKER_STORAGE_CONTAINER="{blob_contaner_name}" \
            -env CP_DEPLOYMENT_ID="my_cloud_pipeline" \
            -env CP_KUBE_MASTER_DOCKER_PATH="/docker/drive/" \
            -env CP_KUBE_MASTER_ETCD_HOST_PATH="/etcd/drive/" \
            -m
```
