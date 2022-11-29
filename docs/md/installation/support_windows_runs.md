# Windows runs support

- [Overview](#overview)
- [Build Windows ami](#build-windows-ami)
- [Build Windows tool](#build-windows-tool)
- [Configure deployment](#configure-deployment)
    - [Configure brand-new deployment](#configure-brand-new-deployment)
    - [Configure existing deployment](#configure-existing-deployment)

## Overview

The following chapters help to configure Windows runs support in Cloud Pipeline.

## Build Windows ami

Base AMI which has to be used for new AMIs building is _ami-041bf3c49db945dbb_. 
Similar AMI in different regions can be found by this [name](https://aws.amazon.com/marketplace/pp/Amazon-Web-Services-Microsoft-Windows-Server-2019-/B07R3BJL99). 
The following steps help to build new version of Cloud Pipeline Windows AMI.

1) Launch an instance using _ami-041bf3c49db945dbb_ instance image, _m5.large_ instance type, proper _subnet_, _100 gb_ disk and proper _security groups_.
Subnet and security groups should be the same as in Cloud Pipeline deployment itself.  
2) Once instance is ready generate password using default private key and connect using RDP to this machine with Administrator login and generated password.  
3) Once connected open powershell console and manually execute contents of _deploy/infra/aws/install-common-win-node.ps1_ script excluding a single `New-EC2Tag` call. 
At this point any changes can be performed on the machine. Notice that before reboot the following command has to be executed otherwise created AMI won't work.

```powershell
C:\ProgramData\Amazon\EC2-Windows\Launch\Scripts\InitializeInstance.ps1 -Schedule
```

4) Create a new ami from the running instance.

## Build Windows tool

The following steps help to build and push a new version of Cloud Pipeline Windows tool:

Launch an instance using ami-041bf3c49db945dbb instance image or launch Windows run in Cloud Pipeline, connect to the instance via RDP or NoMachine, build Window docker image using _deploy/docker/cp-tools/base/windows/Dockerfile_ and push it to Cloud Pipeline private docker registry using the commands below.  
Please use the actual Cloud Pipeline deployment ip.

```powershell
$apiIp = "127.0.0.1"
$registryPort = "31443"
$registry = "${apiIp}:${registryPort}"
$apiUser = "API_USER"
$apiToken = "API_TOKEN"
$tag = "20210610"
@"
{
    "insecure-registries" : ["$registry"],
    "allow-nondistributable-artifacts": ["$registry"]
}
"@ | Out-File -FilePath "c:\programData\docker\config\daemon.json" -Encoding ascii -Force
@"
$apiIp	cp-docker-registry.default.svc.cluster.local
$apiIp	cp-api-srv.default.svc.cluster.local
"@ | Out-File -FilePath "c:\windows\system32\drivers\etc\hosts" -Encoding ascii -Force
docker login "$registry" -u "$apiUser" -p "$apiToken"
docker build -t "$registry/library/windows:$tag" .
docker tag "$registry/library/windows:$tag" "$registry/library/windows:latest"
docker push "$registry/library/windows:$tag"
docker push "$registry/library/windows:latest"
```

## Configure deployment

### Configure brand-new deployment

To configure Windows runs support for brand-new Cloud Pipeline deployments the following actions have to be performed.

1) Update cluster.networks.config system preference by adding platform field to all amis and adding a new Windows ami.

_Before_

```json
{
  "regions": [{
    "amis": [{
      "instance_mask": "*",
      "ami": "...",
      "init_script": "..."
    }, {
      "instance_mask": "p*",
      "ami": "...",
      "init_script": "..."
    }]
  }]
}
```

_After_

```json
{
  "regions": [{
    "amis": [{
      "instance_mask": "*",
      "platform": "linux",
      "ami": "...",
      "init_script": "..."
    }, {
      "instance_mask": "p*",
      "platform": "linux",
      "ami": "...",
      "init_script": "..."
    }, {
      "instance_mask": "*",
      "platform": "windows",
      "ami": "...",
      "init_script": "init_multicloud.ps1"
    }]
  }]
}
```

2) Configure CP_REPO_ENABLED=false run parameter for Windows tool.

### Configure existing deployment

To configure Windows runs support for existing Cloud Pipeline deployments the following actions have to be performed.

1) Two additional parameters have to be added to Kubernetes global config map.

```bash
CP_KUBE_KUBEADM_CERT_HASH="$(openssl x509 -in /etc/kubernetes/pki/ca.crt -noout -pubkey | openssl rsa -pubin -outform DER 2>/dev/null | sha256sum | cut -d' ' -f1)"
CP_KUBE_NODE_TOKEN="$(kubectl --namespace=kube-system describe sa canal \
  | grep Tokens \
  | cut -d: -f2 \
  | xargs kubectl --namespace=kube-system get secret -o json \
  | jq -r '.data.token' \
  | base64 --decode)"
```

2) Update cluster.networks.config system preference by adding platform field to all amis and adding a new Windows ami.

_Before_

```json
{
  "regions": [{
    "amis": [{
      "instance_mask": "*",
      "ami": "...",
      "init_script": "..."
    }, {
      "instance_mask": "p*",
      "ami": "...",
      "init_script": "..."
    }]
  }]
}
```

_After_

```json
{
  "regions": [{
    "amis": [{
      "instance_mask": "*",
      "platform": "linux",
      "ami": "...",
      "init_script": "..."
    }, {
      "instance_mask": "p*",
      "platform": "linux",
      "ami": "...",
      "init_script": "..."
    }, {
      "instance_mask": "*",
      "platform": "windows",
      "ami": "ami-005a8c88514c8ec1e",
      "init_script": "init_multicloud.ps1"
    }]
  }]
}
```

3) Several existing application properties have to be updated.

```
launch.script.url --renamed-to--> launch.script.url.linux
api.security.public.urls <--added-another-public-url-- launch.py
```

4) Several new application properties have to be added.

```properties
launch.script.url.windows=https://${CP_API_SRV_INTERNAL_HOST}:${CP_API_SRV_INTERNAL_PORT}/pipeline/launch.py
kube.kubeadm.cert.hash=${CP_KUBE_KUBEADM_CERT_HASH}
kube.node.token=${CP_KUBE_NODE_TOKEN}
```

5) Kubernetes canal config map has to be updated and all canal pods have to be restarted.  
6) All edge pods have to be restarted.  
7) Configure _CP_REPO_ENABLED=false_ run parameter for Windows tool.
