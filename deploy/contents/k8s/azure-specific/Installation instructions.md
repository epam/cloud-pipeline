# Installation Instructions
**Note:** The instructions are provided for _azure.cloud-pipeline.com_ DNS name case.

### AKS cluster
Make sure cluster subnet security group allows exposure of cloud resources to the Internet

### Database server
Azure Database for PostgreSQL flexible server
If it is a private server, create Private endpoint to allow from AKS virtual network to access the server 
(Networking -> Create private endpoint)

Put admin username and password to a key vault

### Database
Connect to postgres schema, admin user with admin password from the key vault
```
CREATE USER pipeline CREATEDB;
ALTER USER pipeline WITH password 'pipeline';
CREATE DATABASE pipeline OWNER pipeline;
```
Put connection string to config map: PSG_HOST key in cp-config-global.yaml

### Label nodes
```
az aks nodepool update --resource-group <resource-group-name> --cluster-name <cluster-name> --name <node-pool-name> --labels cloud-pipeline/cp-edge="true" cloud-pipeline/cp-api-srv="true" cloud-pipeline/cp-idp="true" cloud-pipeline/region=<region-name> cloud-pipeline/cp-docker-registry="true" cloud-pipeline/scp-executor="true"
```

### Create Azure cloud credentials secret
1. Create a new App registration (cloud-pipeline)
```
az ad sp create-for-rbac --name cloud-pipeline --role Contributor --scopes /subscriptions/<subscription-id> --sdk-auth > azureAuth.json
```
2. Make sure the file is in UTF-8
3. Put the file to the secret (mounted as /root/.cloud, so the file path is /root/.cloud/azureAuth.json)
```
kubectl create secret generic cp-cloud-credentials --from-file="azureAuth.json"
```

### Create cluster ssh key secret
```
ssh-keygen -t rsa -b 4096 -f ./id_rsa_k8s -C "k8s-ssh-key"

kubectl create secret generic cp-cluster-ssh-key --from-file=id_rsa=id_rsa_k8s --from-file=id_rsa.pub=id_rsa_k8s.pub
```

### Create config map
1. Set CP_CLOUD_REGION_ID and PSG_HOST values
2. Create config map
```
kubectl create configmap cp-config-global --from-env-file=cp-config-global.yaml
```

### Create persistent static volume for certificates
```
AZURE_STORAGE_CONNECTION_STRING=$(az storage account show-connection-string -n <storage-account-name> -g <resource-group-name> -o tsv)
az storage share create -n cloud-pipeline-k8s --connection-string $AZURE_STORAGE_CONNECTION_STRING

az storage account keys list --resource-group <resource-group-name> --account-name <storage-account-name> --query "[0].value" -o tsv
kubectl create secret generic azure-secret --from-literal=azurestorageaccountname=<storage-account-name> --from-literal=azurestorageaccountkey=<storage-account-key>
```
change volumeHandle in azure-files-pv if needed
```
kubectl create -f azure-files/azure-files-pv.yaml
kubectl apply -f azure-files/azure-files-mount-options-pvc.yaml
kubectl get pvc azurefile	  
az storage share show --name cloud-pipeline-k8s --account-name <storage-account-name> --account-key <storage-sccount-key> --output table
```

### Set cluster roles
```
kubectl apply -f cluster-role/cluster-role.yaml
kubectl apply -f cluster-role/cluster-role-binding.yaml
```

### Deploy API
```
kubectl apply -f cp-api-srv/cp-api-srv-dpl.yam
kubectl apply -f cp-api-srv/cp-api-srv-svc-ingress.yaml
```
Put CP_API_JWT_ADMIN to config map

### Create Public IP
Public IP name should be then specified in cp-edge-svc+lb.yaml under service.beta.kubernetes.io/azure-pip-name annotation
currently public-IP-name = cloud_pipeline_public_IP
```
az network public-ip create --name <public-IP-name> --resource-group <cluster-resource-group-name> --allocation-method Static --sku Standard
```
Set all external hosts in config map and in dns table
Example
```
<Public-IP> edge.azure.cloud-pipeline.com
<Public-IP> idp.azure.cloud-pipeline.com
<Public-IP> docker.azure.cloud-pipeline.com
<Public-IP> git.azure.cloud-pipeline.com
<Public-IP> azure.cloud-pipeline.com
```

### Setup kube coredns
https://learn.microsoft.com/en-us/azure/aks/coredns-custom
1. Modify core-dns-ms.yaml
2. Apply
```		  
kubectl apply -f core-dns-ms.yaml
```
3. Restart coredns

### Deploy CP Edge
```
kubectl apply -f cp-edge/cp-edge-dpl.yaml
kubectl apply -f cp-edge/cp-edge-svc+lb.yaml
```
Make sure CP_EDGE_CLUSTER_RESOLVER is the same as
```
kubectl exec -it <cp-edge-pod-name> -- cat /etc/resolv.conf
```
Make sure cp-edge service has labels:  
```
kubectl label svc cp-edge "cloud-pipeline/external-host=edge.azure.cloud-pipeline.com"
kubectl label svc cp-edge "cloud-pipeline/external-port=443"
kubectl label svc cp-edge "cloud-pipeline/external-scheme=https"
```

### Deploy IDP
```
kubeclt apply -f cp-idp/cp-idp-dpl.yaml
kubeclt apply -f cp-idp/cp-idp-svc-ingress.yaml
```
on cp-idp pod:
```
curl  "https://cp-idp.default.svc.cluster.local:32080/metadata" -o "cp-api-srv-fed-meta.xml"  -H "Host: idp.azure.cloud-pipeline.com:443" -s -k
```
put the file to /opt/api/sso for idp and api containers (azure DS), port should be 443 

+ idp_pd="${CP_IDP_PROFILE_DB:-/opt/idp/pdb/saml-idp-profiles.json}"
+ cert="/opt/api/pki/sso-public-cert.pem"
+ issuer="https://azure.cloud-pipeline.com:443/pipeline/"
```
saml-idp add-connection "https://azure.cloud-pipeline.com:443/pipeline/" -c "/opt/api/pki/sso-public-cert.pem" --profileDatabase "/opt/idp/pdb/saml-idp-profiles.json"
```

### Docker registry
1. Deploy docker registry:
```
kubeclt apply -f cp-docker-registry/cp-docker-registry-dpl.yaml
kubeclt apply -f cp-docker-registry/cp-docker-registry-svc-ingress.yaml
```

2. Make docker registry available from cluster virtual network on Azure dns resolver:

Create private DNS Zone(azure.cloud-pipeline.com) and record (docker -> service cluster IP) and link to the cluster VNet(make sure the VNet was defined correctly)
https://learn.microsoft.com/en-us/azure/dns/private-dns-getstarted-portal
+ DNS Zone: azure.cloud-pipeline.com
+ Record: docker, type: A, IP address: cp-docker-registry service cluster IP, 

**Note:** For self-signed ssl certificate, add ssl certificate to a node trusted store:

Put docker-public-cert.pem.crt content to the end of registry-ca-ds.yaml file
```
kubeclt apply -f registry-ca-ds.yaml
```
3. Register docker registry docker.azure.cloud-pipeline.com:443 in UI with pipe_admin jwt token as a password

### Create Azure region
+ Name: <region-pretty-name>
+ Storage account: <storage-account-name>
+ Storage account key: <storage-account-key>
+ SSH Public Key Path: /opt/api/pki/id_rsa_k8s.pub
+ Meter Region Name: <region-name>
+ Azure API Url: https://management.azure.com/
+ Price Offer ID: 1
+ Resource group: <cluster-resource-group-name>
+ Auth file: /root/.cloud/azureAuth.json

### System Preferences
+ base.pipe.distributions.url = https://azure.cloud-pipeline.com:443/pipeline/
+ base.api.host = https://azure.cloud-pipeline.com:443/pipeline/restapi/
+ base.api.host.external = https://azure.cloud-pipeline.com:443/pipeline/restapi/
