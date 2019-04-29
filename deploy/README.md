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
export CP_API_DIST_URL=                                             # Specify API distribution tarball URI. If not set - latest version will be used from https://s3.amazonaws.com/cloud-pipeline-oss-builds/builds/latest/cloud-pipeline.latest.tgz

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
                ## Azure
                -env CP_AZURE_STORAGE_ACCOUNT= \                    # Default storage account name, that will be used to manage BLOB/FS storages and persist docker images (if CP_DOCKER_STORAGE_TYPE=obj)
                -env CP_AZURE_STORAGE_KEY= \                        # Key for the default storage account (CP_AZURE_STORAGE_ACCOUNT)
                -env CP_AZURE_DEFAULT_RESOURCE_GROUP= \             # Which Azure resource group will be used by default
                -env CP_AZURE_OFFER_DURABLE_ID = \                  # 
                -env CP_AZURE_SUBSCRIPTION_ID = \                   # 

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

                # Pipectl options
                -m \                                                # Install kuberneters master
                --docker \                                          # Limit images to be pushed during deployment
                -id \                                               # Specify unique ID of the deployment. It will be used to name cloud entities (e.g. path within a docker registry object container). If not set - random 10-char string will be generated
                -s \                                                # Limit services to be installed (e.g. cp-idp, cp-api-srv, etc.)

                # Misc
                -env CP_PREF_STORAGE_SYSTEM_STORAGE_NAME= \         # Name of the object storage, that is used to store system-level data (e.g. issues attachments)
                -env CP_CLOUD_REGION_FILE_STORAGE_HOSTS= \
                -env CP_CUSTOM_USERS_SPEC= \                        # Specify json file with the users to be registered during installation (if pipectl was built using -p /e2e/prerequisites/users.json - test users will be created)
                -env CP_COMMON_SSL_SELF_SIGNED= \                   # Use self-signed or CA signed certificates for SSL (true or false, default: false)
                -env CP_KUBE_MASTER_DOCKER_PATH= \                  # Allows to override a location of the folder where docker stores it's data. This is useful when docker generates too much I/O to the OS Disk and shall be pointed to another device mounted to a more custom location. If not defined - docker defaults are used.
                -env CP_KUBE_MASTER_ETCD_HOST_PATH= \               # Allows to override a location of the folder where etcd stores wal/data dirs. This is useful when etcd runs into I/O latency issues and shall be pointed to another device mounted to a more custom location, which leads to the kube control plane failures. If not defined - /var/lib/etcd path will be used
                -env CP_KUBE_MIN_DNS_REPLICAS= \                    # Allows to configure a minimal number of DNS replicas for the cluster (default: 1). DNS will be autoscaled based on the size of a cluster (1 new replica for each 128 cores or 5 nodes)
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
