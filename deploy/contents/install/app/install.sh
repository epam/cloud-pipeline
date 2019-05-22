#!/bin/bash
# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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


##########
# Preflight setup
##########
INSTALL_SCRIPT_PATH="$( cd "$(dirname "$0")" ; pwd -P )"
K8S_SPECS_HOME=${K8S_SPECS_HOME:-"$INSTALL_SCRIPT_PATH/../../k8s"}

source format-utils.sh
source install-utils.sh
source configure-utils.sh
source docker-utils.sh

print_ok "[Running preflight and dependencies checks]"
run_preflight $# || { print_err "Preflight checks failed, exiting"; exit 1; }
. install-common.sh
if ! init_cloud_config; then
    print_warn "Unable to determine cloud configuration, defaults will be used but installation may fail"
fi
echo

##########
# Configuration setup
##########
print_ok "[Running configuration setup]"

# This function parses any valid command line options and sources install-config file
parse_options "$@"
if [ $? -ne 0 ]; then
    print_err "Unable to setup installation configuration, exiting"
    exit 1
fi
echo

##########
# Validate any extra configuration
##########
if ! validate_cloud_config; then
    print_err "Some of the configuration parameters are not set, or not set correctly, refusing to proceed. Please review any output above"
    exit 1
fi

##########
# Install kube master
##########
print_ok "[Setting up Kube master]"

KUBE_MASTER_IS_INSTALLED=0;
kubectl get no >/dev/null 2>&1 && { KUBE_MASTER_IS_INSTALLED=1;  }

if [ "$KUBE_MASTER_IS_INSTALLED" != 1 ] && [ "$CP_INSTALL_KUBE_MASTER" != 1 ]; then
    print_err "Kube master is not installed or cannot be accessed. Please run installation with -m|--install-kube-master to install master node or enable access to the master from a current node"
    exit 1
fi

if [ "$CP_INSTALL_KUBE_MASTER" == 1 ]; then
    if [ "$KUBE_MASTER_IS_INSTALLED" == 1 ]; then
        print_info "Kube master is already installed, skipping installation"
    else
        print_info "Starting Kube master installation"
        bash install-master.sh
        if [ $? -ne 0 ]; then
            print_err "Errors occured during master installation - please review any output above"
            print_err "Aborting installation"
            exit 1
        fi

        export CP_KUBE_MIN_DNS_REPLICAS=${CP_KUBE_MIN_DNS_REPLICAS:-1}
        print_info "-> Enabling DNS autoscaling with a minimal replicas count: $CP_KUBE_MIN_DNS_REPLICAS"
        kubectl delete configmap "dns-autoscaler" --namespace "kube-system"
        delete_deployment_and_service "dns-autoscaler"
        create_kube_resource $K8S_SPECS_HOME/cp-dns-autoscale/cp-dns-autoscale-dpl.yaml
        print_info "-> Waiting for the DNS autoscaler to initialize"
        wait_for_deployment "dns-autoscaler"

        print_info "-> Configuring Kube DNS well-known entries"
        prepare_kube_dns "$CP_DNS_STATIC_ENTRIES"
    fi
else
    print_info "Kube master installation skipped"
fi
echo

# Initialize kube master host address
print_ok "[Initialize Kube API address]"
export CP_KUBE_EXTERNAL_HOST=${CP_KUBE_EXTERNAL_HOST:-${!CP_KUBE_EXTERNAL_HOST_TYPE}}
update_config_value "$CP_INSTALL_CONFIG_FILE" \
                    "CP_KUBE_EXTERNAL_HOST" \
                    "$CP_KUBE_EXTERNAL_HOST"

CP_KUBE_INTERNAL_HOST=${CP_KUBE_INTERNAL_HOST:-"kubernetes.default.svc.cluster.local"}
print_info "-> Kube API address is set to external: \"$CP_KUBE_EXTERNAL_HOST:$CP_KUBE_EXTERNAL_PORT\", internal: \"$CP_KUBE_INTERNAL_HOST:$CP_KUBE_INTERNAL_PORT\""

CP_KUBE_DNS_HOST=$(get_service_cluster_ip "kube-dns" "kube-system")
if ! grep $CP_KUBE_DNS_HOST /etc/resolv.conf -q; then
    sed -i "1s/^/nameserver $CP_KUBE_DNS_HOST\n/" /etc/resolv.conf
    print_info "-> Kube DNS is set to /etc/resolv.conf (nameserver $CP_KUBE_DNS_HOST)"
fi

if [ -z "$CP_PREF_CLUSTER_PROXIES_DNS_POST" ]; then
    export CP_PREF_CLUSTER_PROXIES_DNS_POST="$CP_KUBE_DNS_HOST"
    update_config_value "$CP_INSTALL_CONFIG_FILE" \
                    "CP_PREF_CLUSTER_PROXIES_DNS_POST" \
                    "$CP_PREF_CLUSTER_PROXIES_DNS_POST"
    print_warn "DNS proxy is not defined, kube-dns $CP_PREF_CLUSTER_PROXIES_DNS_POST will be used for all nodes. If other behavior is expected -please specify it using \"--env CP_PREF_CLUSTER_PROXIES_DNS_POST=\" option"
fi
echo

# Get kubeadm token
print_ok "[Configuring kubeadm credentials]"

set -o pipefail
export CP_KUBE_KUBEADM_TOKEN=$(kubeadm token list | tail -n 1 | cut -f1 -d' ')
set +o pipefail
if [ $? -ne 0 ]; then
    print_err "Errors occured during retrieval of the kubeadm token. Please review any output above, exiting"
    exit 1
else
    print_info "-> kubeadm token retrieved: $CP_KUBE_KUBEADM_TOKEN"
    update_config_value "$CP_INSTALL_CONFIG_FILE" \
                        "CP_KUBE_KUBEADM_TOKEN" \
                        "$CP_KUBE_KUBEADM_TOKEN"
fi
echo


##########
# Setup config for Kube
##########
print_ok "[Creating Kube config map from \"$CP_INSTALL_CONFIG_FILE\"]"

# Cloud Credentials are set as a kubernetes secret
init_kube_secrets

# All config environment variables are set as a kubernetes configmap
init_kube_config_map
echo

##########
# Assign Kube nodes roles
##########
print_ok "[Creating roles to the Kube nodes]"

KUBE_MASTER_NODE_NAME=$(kubectl get nodes --show-labels | grep node-role.kubernetes.io/master | cut -f1 -d' ')

# TODO: allow to specify node name for different roles"

# Allow to schedule API DB to the master
print_info "-> Assigning cloud-pipeline/cp-api-db to $KUBE_MASTER_NODE_NAME"
kubectl label nodes "$KUBE_MASTER_NODE_NAME" cloud-pipeline/cp-api-db="true" --overwrite

# Allow to schedule API Service to the master
print_info "-> Assigning cloud-pipeline/cp-api-srv to $KUBE_MASTER_NODE_NAME"
kubectl label nodes "$KUBE_MASTER_NODE_NAME" cloud-pipeline/cp-api-srv="true" --overwrite

# Allow to schedule GitLab to the master
print_info "-> Assigning cloud-pipeline/cp-git to $KUBE_MASTER_NODE_NAME"
kubectl label nodes "$KUBE_MASTER_NODE_NAME" cloud-pipeline/cp-git="true" --overwrite

# Allow to schedule GitLab to API sync job to the master
print_info "-> Assigning cloud-pipeline/cp-git-sync to $KUBE_MASTER_NODE_NAME"
kubectl label nodes "$KUBE_MASTER_NODE_NAME" cloud-pipeline/cp-git-sync="true" --overwrite

# Allow to schedule basic IdP to the master
print_info "-> Assigning cloud-pipeline/cp-idp to $KUBE_MASTER_NODE_NAME"
kubectl label nodes "$KUBE_MASTER_NODE_NAME" cloud-pipeline/cp-idp="true" --overwrite

# Allow to schedule EDGE to the master
print_info "-> Assigning cloud-pipeline/cp-edge to $KUBE_MASTER_NODE_NAME"
kubectl label nodes "$KUBE_MASTER_NODE_NAME" cloud-pipeline/cp-edge="true" --overwrite
kubectl label nodes "$KUBE_MASTER_NODE_NAME" cloud-pipeline/role="EDGE" --overwrite

# Allow to schedule notifier to the master
print_info "-> Assigning cloud-pipeline/cp-notifier to $KUBE_MASTER_NODE_NAME"
kubectl label nodes "$KUBE_MASTER_NODE_NAME" cloud-pipeline/cp-notifier="true" --overwrite

# Allow to schedule Docker registry to the master
print_info "-> Assigning cloud-pipeline/cp-docker-registry to $KUBE_MASTER_NODE_NAME"
kubectl label nodes "$KUBE_MASTER_NODE_NAME" cloud-pipeline/cp-docker-registry="true" --overwrite

# Allow to schedule Docker comp scanner to the master
print_info "-> Assigning cloud-pipeline/cp-docker-comp to $KUBE_MASTER_NODE_NAME"
kubectl label nodes "$KUBE_MASTER_NODE_NAME" cloud-pipeline/cp-docker-comp="true" --overwrite

# Allow to schedule Clair scanner to the master
print_info "-> Assigning cloud-pipeline/cp-clair to $KUBE_MASTER_NODE_NAME"
kubectl label nodes "$KUBE_MASTER_NODE_NAME" cloud-pipeline/cp-clair="true" --overwrite

# Allow to schedule Search ELK to the master
print_info "-> Assigning cloud-pipeline/cp-search-elk to $KUBE_MASTER_NODE_NAME"
kubectl label nodes "$KUBE_MASTER_NODE_NAME" cloud-pipeline/cp-search-elk="true" --overwrite

# Allow to schedule Search service to the master
print_info "-> Assigning cloud-pipeline/cp-search-srv to $KUBE_MASTER_NODE_NAME"
kubectl label nodes "$KUBE_MASTER_NODE_NAME" cloud-pipeline/cp-search-srv="true" --overwrite

# Allow to schedule Heapster ELK to the master
print_info "-> Assigning cloud-pipeline/cp-heapster-elk to $KUBE_MASTER_NODE_NAME"
kubectl label nodes "$KUBE_MASTER_NODE_NAME" cloud-pipeline/cp-heapster-elk="true" --overwrite

# Allow to schedule Heapster service to the master
print_info "-> Assigning cloud-pipeline/cp-heapster to $KUBE_MASTER_NODE_NAME"
kubectl label nodes "$KUBE_MASTER_NODE_NAME" cloud-pipeline/cp-heapster="true" --overwrite

echo

##########
# Login docker distr registry
##########
print_ok "[Setting up docker distr registry credentials]"

if [ -z "$CP_DOCKER_DIST_USER" ] || [ -z "$CP_DOCKER_DIST_PASS" ]; then
    print_warn "CP_DOCKER_DIST_USER or CP_DOCKER_DIST_PASS is not set, proceeding without registry authentication"
else
    if [ -z "$CP_DOCKER_DIST_SRV" ]; then
        print_warn "CP_DOCKER_DIST_SRV is not set, https://index.docker.io/v1/ is used to authenticate against docker dist registry and create a kube secret"
        export CP_DOCKER_DIST_SRV="https://index.docker.io/v1/"
    fi

    print_info "Logging docker into $CP_DOCKER_DIST_SRV as $CP_DOCKER_DIST_USER"
    docker login -u $CP_DOCKER_DIST_USER -p $CP_DOCKER_DIST_PASS
    if [ $? -ne 0 ]; then
        print_err "Error occured while logging into the distr docker regsitry, exiting"
        exit 1
    fi
    print_info "Trying to delete previous kube secret if exists"
    kubectl delete secret cp-distr-docker-registry-secret

    print_info "Creating kube secret to pull images from $CP_DOCKER_DIST_SRV on behalf of $CP_DOCKER_DIST_USER"
    kubectl create secret   docker-registry \
                            cp-distr-docker-registry-secret \
                            --docker-server="$CP_DOCKER_DIST_SRV" \
                            --docker-username="$CP_DOCKER_DIST_USER" \
                            --docker-password="$CP_DOCKER_DIST_PASS" \
                            --docker-email="noone@nowhere.com"
fi
echo

##########
# Run Pods
##########
CP_INSTALL_SUMMARY=

if is_install_requested; then
    CP_INSTALL_SUMMARY="\n[Summary]\nInstalled services are accessible via the following endpoints:"
fi

# API postgres db
if is_service_requested cp-api-db; then
    print_ok "[Starting postgres DB deployment]"

    print_info "-> Deleting existing instance of postgres DB"
    delete_deployment_and_service   "cp-api-db" \
                                    "/opt/postgresql"


    if is_install_requested; then
        print_info "-> Deploying postgres DB"
        create_kube_resource $K8S_SPECS_HOME/cp-api-db/cp-api-db-dpl.yaml
        create_kube_resource $K8S_SPECS_HOME/cp-api-db/cp-api-db-svc.yaml

        print_info "-> Waiting for postgres DB to initialize"
        wait_for_deployment "cp-api-db"
        CP_INSTALL_SUMMARY="$CP_INSTALL_SUMMARY\ncp-api-db: $PSG_HOST:$PSG_PORT"
    fi
    echo
fi

# SAML IdP
if is_service_requested cp-idp; then
    print_ok "[Starting IdP deployment]"

    print_info "-> Deleting existing instance of IdP"
    delete_deployment_and_service   "cp-idp" \
                                    "/opt/idp"

    if is_install_requested; then
        print_info "-> Creating self-signed SSL certificate for IdP (${CP_IDP_EXTERNAL_HOST}, ${CP_IDP_INTERNAL_HOST})"
        generate_self_signed_key_pair   $CP_IDP_CERT_DIR/ssl-private-key.pem \
                                        $CP_IDP_CERT_DIR/ssl-public-cert.pem \
                                        $CP_IDP_EXTERNAL_HOST \
                                        $CP_IDP_INTERNAL_HOST

        print_info "-> Creating self-signed certificate for IdP (${CP_IDP_EXTERNAL_HOST}, ${CP_IDP_INTERNAL_HOST})"
        generate_self_signed_key_pair   force_self_sign \
                                        $CP_IDP_CERT_DIR/idp-private-key.pem \
                                        $CP_IDP_CERT_DIR/idp-public-cert.pem \
                                        $CP_IDP_EXTERNAL_HOST \
                                        $CP_IDP_INTERNAL_HOST

        print_info "-> Deploying IdP"
        create_kube_resource $K8S_SPECS_HOME/cp-idp/cp-idp-pod.yaml
        create_kube_resource $K8S_SPECS_HOME/cp-idp/cp-idp-svc.yaml
        expose_cluster_port "cp-idp" \
                            "${CP_IDP_EXTERNAL_PORT}" \
                            "8080"
        register_svc_custom_names_in_cluster "cp-idp" "$CP_IDP_EXTERNAL_HOST"

        print_info "-> Waiting for IdP to initialize"
        wait_for_deployment "cp-idp"
        CP_INSTALL_SUMMARY="$CP_INSTALL_SUMMARY\ncp-idp:"
        CP_INSTALL_SUMMARY="$CP_INSTALL_SUMMARY\nUsers auth:     https://$CP_IDP_EXTERNAL_HOST:$CP_IDP_EXTERNAL_PORT"
        CP_INSTALL_SUMMARY="$CP_INSTALL_SUMMARY\nAdministrating: https://$CP_IDP_EXTERNAL_HOST:$CP_IDP_EXTERNAL_PORT/admin"
    fi
    echo
fi

# Node logger - uploads compute host node logs (kubelet/docker/etc..) to the system bucket
print_ok "[Starting Node logger daemonset deployment]"

print_info "-> Deleting existing instance of Node logger daemonset"
kubectl delete daemonset cp-node-logger

print_info "-> Deploying Node logger daemonset"
create_kube_resource $K8S_SPECS_HOME/cp-node-logger/cp-node-logger-ds.yaml


# Heapster (CPU Utilization monitoring)
if is_service_requested cp-heapster; then
    print_ok "[Starting Heapster (CPU Utilization monitoring) service deployment]"

    print_info "-> Deleting existing instance of Heapster ELK service"
    delete_deployment_and_service   "cp-heapster-elk" \
                                    "/opt/heapster-elk"    

    print_info "-> Deleting existing instance of Heapster service"
    delete_deployment_and_service   "cp-heapster" \
                                    "/opt/heapster"

    if is_install_requested; then
        print_info "-> Deploying Heapster ELK service"
        create_kube_resource $K8S_SPECS_HOME/cp-heapster/cp-heapster-elk-dpl.yaml
        create_kube_resource $K8S_SPECS_HOME/cp-heapster/cp-heapster-elk-svc.yaml

        print_info "-> Waiting for Heapster ELK service to initialize"
        wait_for_deployment "cp-heapster-elk"

        CP_INSTALL_SUMMARY="$CP_INSTALL_SUMMARY\ncp-heapster-elk:"
        CP_INSTALL_SUMMARY="$CP_INSTALL_SUMMARY\nElastic:   http://$CP_HEAPSTER_ELK_INTERNAL_HOST:$CP_HEAPSTER_ELK_INTERNAL_PORT"

        print_info "-> Deploying Heapster service"
        create_kube_resource $K8S_SPECS_HOME/cp-heapster/cp-heapster-dpl.yaml
        create_kube_resource $K8S_SPECS_HOME/cp-heapster/cp-heapster-svc.yaml

        print_info "-> Waiting for Heapster service to initialize"
        wait_for_deployment "cp-heapster"

        CP_INSTALL_SUMMARY="$CP_INSTALL_SUMMARY\ncp-heapster: http://$CP_HEAPSTER_INTERNAL_HOST:$CP_HEAPSTER_INTERNAL_PORT"
    fi
    echo
fi

# API Service
# FIXME 1. GPU spot nodes cannot start in AWS. Invalid AZ is seleted. On-demand works fine
if is_service_requested cp-api-srv; then
    print_ok "[Starting API Service deployment]"

    print_info "-> Deleting existing instance of API Service"
    delete_deployment_and_service   "cp-api-srv" \
                                    "/opt/api"

    if is_install_requested; then
        print_info "-> Creating postgres DB user and schema for API Service"
        create_user_and_db  "cp-api-db" \
                            "$PSG_USER" \
                            "$PSG_PASS" \
                            "$PSG_DB"

        print_info "-> Creating self-signed SSL certificate for API Service (${CP_API_SRV_EXTERNAL_HOST}, ${CP_API_SRV_INTERNAL_HOST})"
        generate_self_signed_key_pair   $CP_API_SRV_CERT_DIR/ssl-private-key.pem \
                                        $CP_API_SRV_CERT_DIR/ssl-public-cert.pem \
                                        $CP_API_SRV_EXTERNAL_HOST \
                                        $CP_API_SRV_INTERNAL_HOST && \
        openssl pkcs12 -export  -in $CP_API_SRV_CERT_DIR/ssl-public-cert.pem \
                                -inkey $CP_API_SRV_CERT_DIR/ssl-private-key.pem \
                                -out $CP_API_SRV_CERT_DIR/cp-api-srv-ssl.p12 \
                                -name ssl \
                                -password pass:changeit

        print_info "-> Creating self-signed SSO certificate for API Service (${CP_API_SRV_EXTERNAL_HOST}, ${CP_API_SRV_INTERNAL_HOST})"
        generate_self_signed_key_pair   force_self_sign \
                                        $CP_API_SRV_CERT_DIR/sso-private-key.pem \
                                        $CP_API_SRV_CERT_DIR/sso-public-cert.pem \
                                        $CP_API_SRV_EXTERNAL_HOST \
                                        $CP_API_SRV_INTERNAL_HOST && \
        openssl pkcs12 -export  -in $CP_API_SRV_CERT_DIR/sso-public-cert.pem \
                                -inkey $CP_API_SRV_CERT_DIR/sso-private-key.pem \
                                -out $CP_API_SRV_CERT_DIR/cp-api-srv-sso.p12 \
                                -name sso \
                                -password pass:changeit

        print_info "-> Creating RSA key pair (JWT signing)"
        generate_rsa_key_pair   $CP_API_SRV_CERT_DIR/jwt.key.private \
                                $CP_API_SRV_CERT_DIR/jwt.key.public \
                                "stringify" \
                                $CP_API_SRV_CERT_DIR/jwt.key.x509


        print_info "-> Configuring SSO metadata for the API Service"
        if [ -f "$CP_API_SRV_FED_META_DIR/cp-api-srv-fed-meta.xml" ]; then
            print_warn "SSO Metadata already exists at $CP_API_SRV_FED_META_DIR/cp-api-srv-fed-meta.xml, it will be reused"
            CP_API_SRV_FED_META_EXISTS=1
        else
            print_info "-> Trying to configure SSO metadata using basic IdP (from https://$CP_IDP_INTERNAL_HOST:$CP_IDP_EXTERNAL_PORT/metadata)"
            mkdir -p $CP_API_SRV_FED_META_DIR
            # Waiting a bit to allow IdP to initizalize
            sleep 5
            # Note: HOST header is substituted with the external address, to generate valid binding URLs in the metadata file
            curl    "https://$CP_IDP_INTERNAL_HOST:$CP_IDP_EXTERNAL_PORT/metadata" \
                    -o $CP_API_SRV_FED_META_DIR/cp-api-srv-fed-meta.xml \
                    -H "Host: $CP_IDP_EXTERNAL_HOST:$CP_IDP_EXTERNAL_PORT" \
                    -s \
                    -k
            if [ $? -eq 0 ] && [ -f "$CP_API_SRV_FED_META_DIR/cp-api-srv-fed-meta.xml" ]; then
                CP_API_SRV_FED_META_EXISTS=1
                idp_register_app "https://${CP_API_SRV_EXTERNAL_HOST}:${CP_API_SRV_EXTERNAL_PORT}/pipeline/" \
                                 "$CP_API_SRV_CERT_DIR/sso-public-cert.pem"
            fi
        fi

        if [ -z "$CP_API_SRV_FED_META_EXISTS" ]; then
            print_warn "SSO Metadata was not provided explicitly and/or error occured while getting it from the basic IdP"
            print_warn "API service will attempt to start without metadata, but may fail to initialize"
        fi

        print_info "-> Deploying API Service"
        create_kube_resource $K8S_SPECS_HOME/cp-api-srv/cp-api-srv-dpl.yaml
        create_kube_resource $K8S_SPECS_HOME/cp-api-srv/cp-api-srv-svc.yaml
        expose_cluster_port "cp-api-srv" \
                            "${CP_API_SRV_EXTERNAL_PORT}" \
                            "8080"
        register_svc_custom_names_in_cluster "cp-api-srv" "$CP_API_SRV_EXTERNAL_HOST"

        print_info "-> Waiting for API Service to initialize"
        wait_for_deployment "cp-api-srv"

        print_info "-> Generating admin JWT token for admin user \"$CP_DEFAULT_ADMIN_NAME\""
        CP_API_JWT_ADMIN=$(execute_deployment_command cp-api-srv "java  -jar /opt/api/jwt-generator.jar \
                                                                        --private $CP_API_SRV_CERT_DIR/jwt.key.private \
                                                                        --expires 94608000 \
                                                                        --claim user_id=1 \
                                                                        --claim user_name=$CP_DEFAULT_ADMIN_NAME \
                                                                        --claim role=ROLE_ADMIN \
                                                                        --claim group=ADMIN")
        if [ $? -ne 0 ]; then
            print_err "Error ocurred while generating admin JWT token, docker registry and edge services cannot be configured to integrate with the API Services"
        else
            export CP_API_JWT_ADMIN
            update_config_value "$CP_INSTALL_CONFIG_FILE" \
                                "CP_API_JWT_ADMIN" \
                                "$CP_API_JWT_ADMIN"
            init_kube_config_map
        fi

        print_info "-> Adding default Cloud region configuration"
        api_register_region
        if [ $? -ne 0 ]; then
            print_err "Error ocurred while adding default Cloud region configuration. This can be done manually using the GUI"
        else
            print_ok "Default Cloud region configuration is added"
        fi

        print_info "-> Setting API preference configuration"
        api_setup_base_preferences

        print_info "-> Adding system folder"
        api_register_system_folder

        if [ $? -eq 0 ]; then
            print_info "-> Attaching system storage"
            api_register_system_storage
        fi

        print_info "-> Registering Email templates (Email notification service itself will be installed afterwards)"
        api_register_email_templates

        # if -env CP_CUSTOM_USERS_SPEC= is specified - it will be used, otherwise default one will be tried ($OTHER_PACKAGES_PATH/prerequisites/users.json)
        print_info "-> Registering custom users in IdP and API services"
        api_register_custom_users "$CP_CUSTOM_USERS_SPEC"

        # Here we wait for the price list sync, this is required by the downstream services to manage the instance types configurations            
        if [ -z "$CP_CLOUD_REGION_INTERNAL_ID" ]; then
            print_warn "CP_CLOUD_REGION_INTERNAL_ID is not defined, assuming that a cloud region is not registered correctly previously. WILL NOT wait for price lists synchonization"
        else
            default_instance_type_poll_attempts=100
            default_instance_type_poll_timeout=10
            print_info "-> Verifying that all instance types are synced to the API DB (will poll $default_instance_type_poll_attempts times for the default instance type: ${CP_PREF_CLUSTER_INSTANCE_TYPE})"

            default_instance_type_result=1
            while [ $default_instance_type_result -ne 0 ] || [ ! "$default_instance_type_details" ]; do
                if [ "$default_instance_type_poll_attempts" == 0 ]; then
                    print_err "Unable to get information on the $CP_PREF_CLUSTER_INSTANCE_TYPE instance type after $default_instance_type_poll_attempts attempts"
                    break
                fi

                default_instance_type_details="$(api_get_cluster_instance_details "$CP_PREF_CLUSTER_INSTANCE_TYPE" "$CP_CLOUD_REGION_INTERNAL_ID")"
                default_instance_type_result=$?

                sleep $default_instance_type_poll_timeout
                default_instance_type_poll_attempts=$((default_instance_type_poll_attempts-1))
            done
            if [ "$default_instance_type_result" -eq 0 ]; then
                print_ok "Price list is synchronized to the API DB"
            fi
        fi

        CP_INSTALL_SUMMARY="$CP_INSTALL_SUMMARY\ncp-api-srv: https://$CP_API_SRV_EXTERNAL_HOST:$CP_API_SRV_EXTERNAL_PORT/pipeline/"
    fi
    echo
fi

# Docker registry
if is_service_requested cp-docker-registry; then
    print_ok "[Starting Docker registry deployment]"

    print_info "-> Deleting existing instance of Docker registry"
    delete_deployment_and_service   "cp-docker-registry" \
                                    "/opt/docker-registry" \
                                    "/etc/docker/certs.d/*"

    if is_install_requested; then
        print_info "-> Creating self-signed certificate for Docker registry (${CP_DOCKER_EXTERNAL_HOST}, ${CP_DOCKER_INTERNAL_HOST})"
        generate_self_signed_key_pair   $CP_DOCKER_CERT_DIR/docker-private-key.pem \
                                        $CP_DOCKER_CERT_DIR/docker-public-cert.pem \
                                        $CP_DOCKER_EXTERNAL_HOST \
                                        $CP_DOCKER_INTERNAL_HOST

        print_info "-> Deploying Docker registry"
        create_kube_resource $K8S_SPECS_HOME/cp-docker-registry/cp-docker-registry-dpl.yaml
        create_kube_resource $K8S_SPECS_HOME/cp-docker-registry/cp-docker-registry-svc.yaml
        expose_cluster_port "cp-docker-registry" \
                            "${CP_DOCKER_EXTERNAL_PORT}" \
                            "443"
        register_svc_custom_names_in_cluster "cp-docker-registry" "$CP_DOCKER_EXTERNAL_HOST"

        print_info "-> Waiting for Docker registry to initialize"
        wait_for_deployment "cp-docker-registry"

        print_info "-> Register docker registry in API Service"
        api_register_docker_registry
        CP_DOCKER_INSTALLED=$?
        if [ $CP_DOCKER_INSTALLED -ne 0 ]; then
            print_err "Default docker registry registration failed. You can configure it manually through the Cloud Pipeline GUI/API"
        fi

        print_info "-> Push base tools images into the docker registry"
        CP_DOCKER_MANIFEST_PATH=${CP_DOCKER_MANIFEST_PATH:-"$INSTALL_SCRIPT_PATH/../../dockers-manifest"}
        if [ $CP_DOCKER_INSTALLED -eq 0 ] && [ -d "$CP_DOCKER_MANIFEST_PATH" ]; then
            docker_push_manifest "$(realpath $CP_DOCKER_MANIFEST_PATH)" "$CP_DOCKER_REGISTRY_ID"
            if [ $? -ne 0 ]; then
                print_err "Errors occured during dockers push/registritation. Some of the images may not be registered"
            else
                print_ok "All docker images are registered"
            fi
        else
            print_err "Default docker registry was not registered correctly or a manifest file is not available ($CP_DOCKER_MANIFEST_PATH), base tools images WILL NOT be pushed"
        fi
        CP_INSTALL_SUMMARY="$CP_INSTALL_SUMMARY\ncp-docker-registry: $CP_DOCKER_EXTERNAL_HOST:$CP_DOCKER_EXTERNAL_PORT"
    fi
    echo
fi

# EDGE
if is_service_requested cp-edge; then
    print_ok "[Starting EDGE deployment]"

    print_info "-> Deleting existing instance of EDGE"
    delete_deployment_and_service   "cp-edge" \
                                    "/opt/edge"

    if is_install_requested; then   
        print_info "-> Creating self-signed SSL certificate for EDGE (${CP_EDGE_EXTERNAL_HOST}, ${CP_EDGE_INTERNAL_HOST})"
        generate_self_signed_key_pair   $CP_EDGE_CERT_DIR/ssl-private-key.pem \
                                        $CP_EDGE_CERT_DIR/ssl-public-cert.pem \
                                        $CP_EDGE_EXTERNAL_HOST \
                                        $CP_EDGE_INTERNAL_HOST

        export EDGE_EXTERNAL_SCHEMA="https"
        update_config_value "$CP_INSTALL_CONFIG_FILE" \
                                "EDGE_EXTERNAL_SCHEMA" \
                                "$EDGE_EXTERNAL_SCHEMA"

        export API_EXTERNAL="https://$CP_API_SRV_EXTERNAL_HOST:$CP_API_SRV_EXTERNAL_PORT/pipeline/restapi/"
        update_config_value "$CP_INSTALL_CONFIG_FILE" \
                                "API_EXTERNAL" \
                                "$API_EXTERNAL"

        export EDGE_EXTERNAL="$CP_EDGE_EXTERNAL_HOST:$CP_EDGE_EXTERNAL_PORT"
        update_config_value "$CP_INSTALL_CONFIG_FILE" \
                                "EDGE_EXTERNAL" \
                                "$EDGE_EXTERNAL"
        
        init_kube_config_map

        print_ok "-> EDGE addresses parameters set:"
        print_ok "   EDGE_EXTERNAL_SCHEMA:  $EDGE_EXTERNAL_SCHEMA"
        print_ok "   API_EXTERNAL:          $API_EXTERNAL"
        print_ok "   EDGE_EXTERNAL:         $EDGE_EXTERNAL"

        print_info "-> Deploying EDGE"
        create_kube_resource $K8S_SPECS_HOME/cp-edge/cp-edge-dpl.yaml
        create_kube_resource $K8S_SPECS_HOME/cp-edge/cp-edge-svc.yaml
        expose_cluster_port "cp-edge" \
                            "${CP_EDGE_EXTERNAL_PORT}" \
                            "8080"
        expose_cluster_port "cp-edge" \
                            "${CP_EDGE_CONNECT_EXTERNAL_PORT}" \
                            "8081"
        register_svc_custom_names_in_cluster "cp-edge" "$CP_EDGE_EXTERNAL_HOST"

        print_info "-> Waiting for EDGE to initialize"
        wait_for_deployment "cp-edge"

        print_info "-> Setting EDGE Service labels:"
        __edge_external_host__="cloud-pipeline/external-host=$CP_EDGE_EXTERNAL_HOST"
        echo $__edge_external_host__
        kubectl label svc cp-edge $__edge_external_host__

        __edge_external_port__="cloud-pipeline/external-port=$CP_EDGE_EXTERNAL_PORT"
        echo $__edge_external_port__
        kubectl label svc cp-edge $__edge_external_port__

        __edge_external_schema__="cloud-pipeline/external-scheme=$EDGE_EXTERNAL_SCHEMA"
        echo $__edge_external_schema__
        kubectl label svc cp-edge $__edge_external_schema__

        CP_INSTALL_SUMMARY="$CP_INSTALL_SUMMARY\ncp-edge:" 
        CP_INSTALL_SUMMARY="$CP_INSTALL_SUMMARY\nSSH+ENDPOINTS: $EDGE_EXTERNAL_SCHEMA://$CP_EDGE_EXTERNAL_HOST:$CP_EDGE_EXTERNAL_PORT"
        CP_INSTALL_SUMMARY="$CP_INSTALL_SUMMARY\nHTTP CONNECT:  $EDGE_EXTERNAL_SCHEMA://$CP_EDGE_EXTERNAL_HOST:$CP_EDGE_CONNECT_EXTERNAL_PORT"
    fi
    echo
fi

# GitLab
# FIXME 1. External IP is used to configure client credentials in a container. Fix API?
if is_service_requested cp-git; then
    print_ok "[Starting GitLab deployment]"

    print_info "-> Deleting existing instance of GitLab"
    delete_deployment_and_service   "cp-git" \
                                    "/opt/gitlab"

    if is_install_requested; then
        print_info "-> Creating postgres DB user and schema for GitLab"
        create_user_and_db  "cp-api-db" \
                            "$GITLAB_DATABASE_USERNAME" \
                            "$GITLAB_DATABASE_PASSWORD" \
                            "$GITLAB_DATABASE_DATABASE"

        print_info "-> Creating self-signed SSL certificate for GitLab (${CP_GITLAB_EXTERNAL_HOST}, ${CP_GITLAB_INTERNAL_HOST})"
        generate_self_signed_key_pair   $CP_GITLAB_CERT_DIR/ssl-private-key.pem \
                                        $CP_GITLAB_CERT_DIR/ssl-public-cert.pem \
                                        $CP_GITLAB_EXTERNAL_HOST \
                                        $CP_GITLAB_INTERNAL_HOST

        print_info "-> Creating self-signed SSO certificate for GitLab (${CP_GITLAB_EXTERNAL_HOST}, ${CP_GITLAB_INTERNAL_HOST})"
        generate_self_signed_key_pair   force_self_sign \
                                        $CP_GITLAB_CERT_DIR/sso-private-key.pem \
                                        $CP_GITLAB_CERT_DIR/sso-public-cert.pem \
                                        $CP_GITLAB_EXTERNAL_HOST \
                                        $CP_GITLAB_INTERNAL_HOST

        print_info "-> Deploying GitLab"
        create_kube_resource $K8S_SPECS_HOME/cp-git/cp-git-dpl.yaml
        create_kube_resource $K8S_SPECS_HOME/cp-git/cp-git-svc.yaml
        expose_cluster_port "cp-git" \
                            "${CP_GITLAB_EXTERNAL_PORT}" \
                            "${CP_GITLAB_INTERNAL_PORT}"
        register_svc_custom_names_in_cluster "cp-git" "$CP_GITLAB_EXTERNAL_HOST"

        # For gitlab we are waiting for endpoint to be alive (return redirect to IdP) as kube readiness probe cannot handle redirects
        print_info "-> Waiting for GitLab to initialize"
        wait_for_service "https://$CP_GITLAB_INTERNAL_HOST:$CP_GITLAB_EXTERNAL_PORT/" "302"

        GITLAB_ROOT_TOKEN=""
        GITLAB_ROOT_TOKEN_ATTEMPTS=60
        CP_GITLAB_INIT_TIMEOUT=30
        print_info "-> Getting GitLab root's private_token (gitlab will be pinged $GITLAB_ROOT_TOKEN_ATTEMPTS times as it may still be not initialized...)"
        while [ -z "$GITLAB_ROOT_TOKEN" ] || [ "$GITLAB_ROOT_TOKEN" == "null" ]; do
            if [ "$GITLAB_ROOT_TOKEN_ATTEMPTS" == 0 ]; then
                print_err "Unable to get GitLab root's private_token after $GITLAB_ROOT_TOKEN_ATTEMPTS attempts"
                break
            fi
            GITLAB_ROOT_TOKEN=$(curl -k https://$CP_GITLAB_INTERNAL_HOST:$CP_GITLAB_EXTERNAL_PORT/api/v4/session -s -F "login=$GITLAB_ROOT_USER" -F "password=$GITLAB_ROOT_PASSWORD" | jq -r '.private_token')
            sleep 10
            GITLAB_ROOT_TOKEN_ATTEMPTS=$((GITLAB_ROOT_TOKEN_ATTEMPTS-1))
        done
        if [ "$GITLAB_ROOT_TOKEN" ] && [ "$GITLAB_ROOT_TOKEN" != "null" ]; then
            print_ok "GitLab token retrieved: $GITLAB_ROOT_TOKEN"
            export GITLAB_ROOT_TOKEN
            update_config_value "$CP_INSTALL_CONFIG_FILE" \
                                "GITLAB_ROOT_TOKEN" \
                                "$GITLAB_ROOT_TOKEN"
            init_kube_config_map

            print_info "Waiting $CP_GITLAB_INIT_TIMEOUT seconds, before getting impersonation token (while root token is retrieved - gitlab may still fail with 502)"
            print_info "-> Getting GitLab root's impersonation token"
            sleep $CP_GITLAB_INIT_TIMEOUT
            GITLAB_IMP_TOKEN=$(curl -k \
                                    --request POST \
                                    --silent \
                                    --header "PRIVATE-TOKEN: $GITLAB_ROOT_TOKEN" \
                                    --data "name=CloudPipeline" \
                                    --data "scopes[]=api" https://$CP_GITLAB_INTERNAL_HOST:$CP_GITLAB_EXTERNAL_PORT/api/v4/users/1/impersonation_tokens | jq -r '.token')
            if [ "$GITLAB_IMP_TOKEN" ] && [ "$GITLAB_IMP_TOKEN" != "null" ]; then
                print_ok "GitLab impersonation token retrieved: $GITLAB_IMP_TOKEN"
                export GITLAB_IMP_TOKEN
                update_config_value "$CP_INSTALL_CONFIG_FILE" \
                                    "GITLAB_IMP_TOKEN" \
                                    "$GITLAB_IMP_TOKEN"
                init_kube_config_map

                print_info "-> Setting trust for GitLab SSL certificate in API Services"
                execute_deployment_command cp-api-srv "/update-trust $CP_GITLAB_CERT_DIR/ssl-public-cert.pem cp-git"

                print_info "-> Register GitLab in API Services"
                print_info "Waiting $CP_GITLAB_INIT_TIMEOUT seconds, before registration (while health endpoint reported OK - gitlab may still fail with 502)"
                sleep $CP_GITLAB_INIT_TIMEOUT
                api_register_gitlab "$GITLAB_IMP_TOKEN"

                idp_register_app "https://${CP_GITLAB_EXTERNAL_HOST}:${CP_GITLAB_EXTERNAL_PORT}" \
                                 "$CP_GITLAB_CERT_DIR/sso-public-cert.pem"

                print_info "-> Registering DataTransfer pipeline"
                api_register_data_transfer_pipeline

                if [ "$CP_DEPLOY_DEMO" ]; then
                    print_info "-> Registering Demo pipelines"
                    api_register_demo_pipelines
                fi
            else
                print_err "Error occured while getting GitLab root's impersonation token (https://$CP_GITLAB_INTERNAL_HOST:$CP_GITLAB_EXTERNAL_PORT/). GitLab won't be registered, but this can be done manually from the Cloud Pipeline GUI/API"
            fi
        else
            print_err "Error occured while getting GitLab root's private_token (https://$CP_GITLAB_INTERNAL_HOST:$CP_GITLAB_EXTERNAL_PORT/). GitLab won't be registered, but this can be done manually from the Cloud Pipeline GUI/API"
        fi

        CP_INSTALL_SUMMARY="$CP_INSTALL_SUMMARY\ncp-git:"
        CP_INSTALL_SUMMARY="$CP_INSTALL_SUMMARY\nSAML Auth:       https://$CP_GITLAB_EXTERNAL_HOST:$CP_GITLAB_EXTERNAL_PORT"
        CP_INSTALL_SUMMARY="$CP_INSTALL_SUMMARY\nLogin/Pass Auth: https://$CP_GITLAB_EXTERNAL_HOST:$CP_GITLAB_EXTERNAL_PORT/users/sign_in?auto_sign_in=false"
    fi
    echo
fi

# Git Sync
if is_service_requested cp-git-sync; then
    print_ok "[Starting Git Sync deployment]"

    print_info "-> Deleting existing instance of Git Sync"
    delete_deployment_and_service   "cp-git-sync" \
                                    "/opt/git-sync"

    if is_install_requested; then
        if [ -z "$CP_API_JWT_ADMIN" ]; then
            print_warn "API JWT token is not set via (CP_API_JWT_ADMIN). Process will proceed"
            print_warn "Make sure CP_API_JWT_ADMIN is available in the Kube \"cp-config-global\" configmap, otherwise - rerun cp-api and cp-git-sync deployment"
        fi
        print_info "-> Deploying Git Sync"
        create_kube_resource $K8S_SPECS_HOME/cp-git-sync/cp-git-sync-pod.yaml

        print_info "-> Waiting for Git Sync to initialize"
        wait_for_deployment "cp-git-sync"
        CP_INSTALL_SUMMARY="$CP_INSTALL_SUMMARY\ncp-git-sync: deployed"
    fi
    echo
fi

# Docker comp scanner
if is_service_requested cp-docker-comp; then
    print_ok "[Starting Docker components scanner deployment]"

    print_info "-> Deleting existing instance of Docker components scanner"
    delete_deployment_and_service   "cp-docker-comp" \
                                    "/opt/docker-comp"

    if is_install_requested; then
        print_info "-> Deploying Docker components scanner"
        create_kube_resource $K8S_SPECS_HOME/cp-docker-comp/cp-docker-comp-dpl.yaml
        create_kube_resource $K8S_SPECS_HOME/cp-docker-comp/cp-docker-comp-svc.yaml

        print_info "-> Waiting for Docker components scanner to initialize"
        wait_for_deployment "cp-docker-comp"

        CP_DOCKER_COMP_INIT_TIMEOUT=30
        print_info "-> Register Docker components scanner in API Services"
        print_info "Waiting $CP_DOCKER_COMP_INIT_TIMEOUT seconds, before registration (no health endpoint available to validate correctly)"
        sleep $CP_DOCKER_COMP_INIT_TIMEOUT
        api_register_docker_comp

        CP_INSTALL_SUMMARY="$CP_INSTALL_SUMMARY\ncp-docker-comp: http://${CP_DOCKER_COMP_INTERNAL_HOST}:${CP_DOCKER_COMP_INTERNAL_PORT}/dockercompscan/"
    fi
    echo
fi

# Clair
if is_service_requested cp-clair; then
    print_ok "[Starting Clair deployment]"

    print_info "-> Deleting existing instance of Clair"
    delete_deployment_and_service   "cp-clair" \
                                    "/opt/clair"

    if is_install_requested; then
        print_info "-> Creating postgres DB user and schema for Clair"
        create_user_and_db  "cp-api-db" \
                            "$CP_CLAIR_DATABASE_USERNAME" \
                            "$CP_CLAIR_DATABASE_PASSWORD" \
                            "$CP_CLAIR_DATABASE_DATABASE"

        print_info "-> Deploying Clair"
        create_kube_resource $K8S_SPECS_HOME/cp-clair/cp-clair-dpl.yaml
        create_kube_resource $K8S_SPECS_HOME/cp-clair/cp-clair-svc.yaml

        print_info "-> Waiting for Clair to initialize"
        wait_for_deployment "cp-clair"

        print_info "-> Register Clair in API Services"
        api_register_clair

        CP_INSTALL_SUMMARY="$CP_INSTALL_SUMMARY\ncp-clair: http://${CP_CLAIR_INTERNAL_HOST}:${CP_CLAIR_INTERNAL_PORT}"
    fi
    echo
fi

# Notifier
if is_service_requested cp-notifier; then
    print_ok "[Starting Email notifier deployment]"

    print_info "-> Deleting existing instance of the Email notifier"
    delete_deployment_and_service   "cp-notifier" \
                                    "/opt/notifier"

    if is_install_requested; then
        CP_NOTIFIER_SMTP_PARAMETERS_LIST="CP_NOTIFIER_SMTP_SERVER_HOST \
                                          CP_NOTIFIER_SMTP_SERVER_PORT \
                                          CP_NOTIFIER_SMTP_FROM \
                                          CP_NOTIFIER_SMTP_USER \
                                          CP_NOTIFIER_SMTP_PASS"
        if ! check_params_present "update_config" $CP_NOTIFIER_SMTP_PARAMETERS_LIST; then
            print_err "Not all the SMTP parameters are set ("$CP_NOTIFIER_SMTP_PARAMETERS_LIST"). Email notifier service WILL NOT be installed. Please rerun installation with \"-s cp-notifier\" and all the parameters specified"
        else
            print_info "-> Deploying Email notifier"
            create_kube_resource $K8S_SPECS_HOME/cp-notifier/cp-notifier-pod.yaml

            print_info "-> Waiting for the Email notifier to initialize"
            wait_for_deployment "cp-notifier"

            CP_INSTALL_SUMMARY="$CP_INSTALL_SUMMARY\ncp-notifier: deployed"
        fi
    fi
    echo
fi

# Search
if is_service_requested cp-search; then
    print_ok "[Starting Search service deployment]"

    print_info "-> Deleting existing instance of Search service"
    delete_deployment_and_service   "cp-search-srv" \
                                    "/opt/search"

    print_info "-> Deleting existing instance of Search ELK service"
    delete_deployment_and_service   "cp-search-elk" \
                                    "/opt/search-elk"    

    if is_install_requested; then
        print_info "-> Deploying Search ELK service"
        create_kube_resource $K8S_SPECS_HOME/cp-search/cp-search-elk-dpl.yaml
        create_kube_resource $K8S_SPECS_HOME/cp-search/cp-search-elk-svc.yaml

        print_info "-> Waiting for Search ELK service to initialize"
        wait_for_deployment "cp-search-elk"

        CP_INSTALL_SUMMARY="$CP_INSTALL_SUMMARY\ncp-search-elk:"
        CP_INSTALL_SUMMARY="$CP_INSTALL_SUMMARY\nElastic:   http://$CP_SEARCH_ELK_INTERNAL_HOST:$CP_SEARCH_ELK_ELASTIC_INTERNAL_PORT"
        CP_INSTALL_SUMMARY="$CP_INSTALL_SUMMARY\nKibana:    http://$CP_SEARCH_ELK_INTERNAL_HOST:$CP_SEARCH_ELK_KIBANA_INTERNAL_PORT"

        print_info "-> Deploying Search service"
        create_kube_resource $K8S_SPECS_HOME/cp-search/cp-search-srv-pod.yaml
        create_kube_resource $K8S_SPECS_HOME/cp-search/cp-search-srv-svc.yaml

        print_info "-> Waiting for Search service to initialize"
        wait_for_deployment "cp-search-srv"

        print_info "-> Registering Search service in API"
        api_register_search

        CP_INSTALL_SUMMARY="$CP_INSTALL_SUMMARY\ncp-search-srv: http://$CP_SEARCH_INTERNAL_HOST:$CP_SEARCH_INTERNAL_PORT"
    fi
    echo
fi

# WebDav

# Share Service
print_ok "Installation done"
echo -e $CP_INSTALL_SUMMARY
