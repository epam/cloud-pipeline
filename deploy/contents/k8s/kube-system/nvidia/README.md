## Install nvidia plugin
```
kubectl apply -f https://raw.githubusercontent.com/epam/cloud-pipeline/refs/heads/develop/deploy/contents/k8s/kube-system/nvidia/nvidia-device-plugin.yaml
```

## Install k8s-fuse-plugin
```
kubectl apply -f https://raw.githubusercontent.com/sidoruka/k8s-fuse-plugin/refs/heads/master/manifests/k8s-fuse-plugin.yml
```

## Hot node pool settings
* Labels:
  * `CP_USE_NODES_COUNT_INFORMATION: false`
  * `nvidia-gpu-type: h100`
  * `CP_ENABLE_FUSE_DEVICE: true`
 
## Preferences
* launch.container.requests.mapping
```
{
 "CP_CAP_REQUESTS_GPU": {
  "name": "nvidia.com/gpu",
  "requests": false,
  "limits": true
 }
}
```

* launch.reservation.parameters
```
{
 "p5.48xlarge": {
  "gpu_requests_enabled": true,
  "cpu_requests_enabled": false,
  "ram_requests_enabled": false,
  "parameters": {
   "CP_CAP_EBS_VOLUMES_MOUNT_DISABLED": "true"
  },
  "kube_assign_policy": {
   "skipContainerRequests": false,
   "selector": {
    "label": "nvidia-gpu-type",
    "value": "h100"
   }
  }
 }
}
```

* launch.kube.skip.reassign.labels
```
{
 "CP_USE_NODES_COUNT_INFORMATION": "false"
}
```
