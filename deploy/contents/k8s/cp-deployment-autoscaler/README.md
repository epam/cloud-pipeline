# Deployment autoscaler

> [Issue #2639](https://github.com/epam/cloud-pipeline/issues/2639)

Deployment autoscaler horizontally autoscales both kubernetes deployments and kubernetes nodes in order to achieve some predefined target utilization.

## Deployment

In order to deploy deployment autoscaler for some kubernetes deployment use the following code snippet.

```bash
# Download deployment autoscaler resources
CP_WORKDIR="$(mktemp -d)"
CP_URL="https://raw.githubusercontent.com/epam/cloud-pipeline/develop/deploy/contents/k8s/cp-deployment-autoscaler/"
wget "${CP_URL}/cp-deployment-autoscaler-dpl.yaml" -O "${CP_WORKDIR}/cp-deployment-autoscaler-dpl.yaml"
wget "${CP_URL}/config.json" -O "${CP_WORKDIR}/config.json"

# Specify autoscaler settings
export CP_DEPLOYMENT_AUTOSCALER_DEPLOYMENT_NAME="cp-api-srv-autoscaler"
export CP_DEPLOYMENT_AUTOSCALER_CONFIGMAP_NAME="${CP_DEPLOYMENT_AUTOSCALER_DEPLOYMENT_NAME}-config"
export CP_DEPLOYMENT_AUTOSCALER_DEPLOYMENT_FILE="${CP_WORKDIR}/cp-deployment-autoscaler-dpl.yaml"
export CP_DEPLOYMENT_AUTOSCALER_CONFIG_FILE="${CP_WORKDIR}/config.json"
export CP_DOCKER_DIST_SRV="${CP_DOCKER_DIST_SRV:-"quay.io/"}"
export CP_VERSION="${CP_VERSION:-"0.17"}"

# Prepare autoscaler configuration
nano "${CP_DEPLOYMENT_AUTOSCALER_CONFIG_FILE}"

# Create or replace autoscaler deployment configuration
if kubectl get cm "${CP_DEPLOYMENT_AUTOSCALER_CONFIGMAP_NAME}"; then
  kubectl delete cm "${CP_DEPLOYMENT_AUTOSCALER_CONFIGMAP_NAME}"
fi
kubectl create cm "${CP_DEPLOYMENT_AUTOSCALER_CONFIGMAP_NAME}" --from-file="${CP_DEPLOYMENT_AUTOSCALER_CONFIG_FILE}"

# Prepare autoscaler deployment configuration
envsubst < "${CP_DEPLOYMENT_AUTOSCALER_DEPLOYMENT_FILE}" > "${CP_WORKDIR}/_tmp"
cp "${CP_WORKDIR}/_tmp" "${CP_DEPLOYMENT_AUTOSCALER_DEPLOYMENT_FILE}"

# Create or replace autoscaler deployment
if kubectl get deploy "${CP_DEPLOYMENT_AUTOSCALER_DEPLOYMENT_NAME}"; then
  kubectl delete deploy "${CP_DEPLOYMENT_AUTOSCALER_DEPLOYMENT_NAME}"
fi
kubectl apply -f "${CP_DEPLOYMENT_AUTOSCALER_DEPLOYMENT_FILE}"

# Cleanup resources
rm "${CP_WORKDIR}"
```

## Configuration

Deployment autoscaler parameter descriptions can be found in the following code snippet.

```json
{
  "target": {
    // Specifies kubernetes deployments to autoscale
    "deployments": [
      "cp-api-srv"
    ],
    
    // Specifies kubernetes labels of deployment nodes
    "labels": {
      "cloud-pipeline/cp-api-srv": "true"
    },
    
    // Specifies kubernetes labels of deployment transient nodes
    "transient_labels": {
      "cloud-pipeline/persistence": "transient"
    },
    
    // Specifies tags of deployment instances
    "tags": {
      "cloud-pipeline/environment": "dev",
      "cloud-pipeline/deployment": "cp-api-srv"
    },
    
    // Specifies tags of deployment transient instances
    "transient_tags": {
      "cloud-pipeline/persistence": "transient"
    },
    
    // Specifies kubernetes deployment labels to check for reserved pods
    "reserved_labels": [
      "cp-api-srv/service-leader"
    ],
    
    // Specifies instances which cannot be scaled down
    "forbidden_instances": [
      "i-12345678901234567"
    ],
    
    // Specifies kubernetes nodes which cannot be scaled down
    "forbidden_nodes": [
      "ip-123-45-6-789.eu-central-1.compute.internal"
    ]
  },
  "trigger": {
    // Specifies cluster nodes per target replicas coefficient.
    // The autoscaler tries to minimize a difference between 
    // the actual and target coefficient value by scaling replicas.
    "cluster_nodes_per_target_replicas": 100,
    
    // Specifies target replicas per target nodes coefficient.
    // The autoscaler tries to minimize a difference between 
    // the actual and target coefficient value by scaling nodes.
    "target_replicas_per_target_nodes": 1,
    
    // Specifies number of memory pressured target nodes 
    // which triggers nodes/replicas scaling.
    "memory_pressured_nodes": 1,
    
    // Specifies number of disk pressured target nodes 
    // which triggers nodes/replicas scaling.
    "disk_pressured_nodes": 1,
    
    // Specifies number of pid pressured target nodes 
    // which triggers nodes/replicas scaling.
    "pid_pressured_nodes": 1,
    
    "cpu_utilization": {
      // Specifies percent of target nodes cpu utilization
      // which triggers nodes/replicas scaling ↑.
      "max": 90,
      
      // Specifies cpu utilization monitoring period in seconds.
      "monitoring_period": 600
    },
    
    "memory_utilization": {
      // Specifies percent of target nodes memory utilization  
      // which triggers nodes/replicas scaling ↑.
      "max": 90,
      
      // Specifies memory utilization monitoring period in seconds.
      "monitoring_period": 600
    }
  },
  "rules": {
    // Specifies how to handle instances which doesn't have corresponding nodes:
    //   SKIP - skips such instances explicit processing.
    //   STOP - terminates such instances.
    "on_lost_instances" : "SKIP|STOP",
    
    // Specifies how to handle nodes which doesn't have corresponding instances:
    //   SKIP - skips such nodes explicit processing.
    //   STOP - deletes such nodes.
    "on_lost_nodes": "SKIP|STOP",
    
    "on_threshold_trigger": {
      // Specifies how many extra replicas can be scaled ↑ if some threshold triggers.
      "extra_replicas": 2,
      
      // Specifies how many extra nodes can be scaled ↑ if some threshold triggers.
      "extra_nodes": 2
    }
  },
  "limit": {
    // Specifies minimum number of deployment nodes.
    "min_nodes_number": 1,
    
    // Specifies maximum number of deployment nodes.
    "max_nodes_number": 10,
    
    // Specifies minimum number of deployment replicas.
    "min_replicas_number": 1,
        
    // Specifies maximum number of deployment replicas.
    "max_replicas_number": 10,
    
    // Specifies minimum interval between two consequent scalings in seconds.
    "min_scale_interval": 300,
    
    // Specifies minimum trigger duration before scaling in seconds.
    "min_triggers_duration": 60
  },
  "instance": { 
    "instance_cloud": "aws",
    "instance_region": "eu-central-1",
    "instance_image": "ami-12345678901234567",
    "instance_type": "m5.xlarge",
    "instance_disk": 500,
    "instance_sshkey": "deploykey",
    "instance_subnet": "subnet-12345678",
    "instance_security_groups": [
      "sg-12345678"
    ],
    "instance_role": "arn:aws:iam::123456789012:instance-profile/Cloud-Pipeline-Service",
    "instance_name": "cp-deployment-autoscaler-instance",
    "instance_init_script": "/opt/deployment-autoscaler/init_multicloud.sh"
  },
  "node": {
    "kube_token": "12345678901234567890123",
    "kube_ip": "123.45.6.789",
    "kube_port": "6443",
    "kube_dns_ip": "10.96.0.10",
    "aws_fs_url": "fs-12345678901234567.fsx.eu-central-1.amazonaws.com@tcp:/12345678",
    "http_proxy": "",
    "https_proxy": "",
    "no_proxy": ""
  },
  "timeout": {
    // Specifies node scaling ↑ polling timeout.
    "scale_up_node_timeout": 900,
    
    // Specifies node scaling ↑ polling delay.
    "scale_up_node_delay": 10,
    
    // Specifies instances scaling ↑ polling timeout.
    "scale_up_instance_timeout": 60,
    
    // Specifies instances scaling ↑ polling delay.
    "scale_up_instance_delay": 10,
    
    // Specifies node scaling ↓ polling timeout.
    "scale_down_node_timeout": 120,
    
    // Specifies node scaling ↓ polling delay.
    "scale_down_node_delay": 10
  },
  "misc": {
    "boto3_retry_count": 10
  }
}
```
