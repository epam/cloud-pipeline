# How to enable Windows machines on AWS-native Cloud-Pipeline deployment:
 1. Build new base ami from Windows_Server-2019-English-Full-EKS_Optimized-1.29 ami:
   - run node
   - install prereq: as in install-common-win-node.ps1 NOTE: use RPD and open powershell from here, windows has a bug with remote powershell session -> not all packages will be able to install through ssh or aws ssm session
   - created image
   
 2. update cluster.networks.config with windows entry:
  {
     "platform": "windows",
     "instance_mask": "*",
     "ami": "<new built AMI id>",
     "init_script": "/opt/api/scripts/init_aws_native.ps1",
     "additional_spec": {
      "IamInstanceProfile": {
       "Arn": "<ARN IP of worker nodes>"
      }
  }
  
 3. Double check that cluster.networks.config has tags like:
   "eks:cluster-name": "<eks-cluster-name>",
   "kubernetes.io/cluster/<eks-cluster-name>": "owned"
   
   it is needed for EKS to configure windows node to join cluster correctly and fully finish initialization
   
 4. Build windows docker from deploy/docker/cp-tools/base/windows
 
   # Run windows machine
   
   # Install docker
   https://learn.microsoft.com/en-us/virtualization/windowscontainers/quick-start/set-up-environment?tabs=dockerce#windows-server-1


   # Allow to push win image to docker registry C:\ProgramData\docker\config\daemon.json: 
    {
      "hosts":  ["npipe://"],
      "allow-nondistributable-artifacts": ["<registry-address>"]
    }
    
 5. Configure tool with params:
 
   CP_DESKTOP_PROXY_HOST:	<edge-adress>
   CP_DESKTOP_PROXY_PORT:	443
   CP_NICE_DCV_DESKTOP_PORT:	8443
   CP_NICE_DCV_SERVING_PORT:	8081
   CP_NOMACHINE_DESKTOP_PORT:	4000
   CP_NOMACHINE_SERVING_PORT:	8080
   
 6. Configure endpoints:
   8443 - NICE DCV web
   8081 - NICE DCV desktop
   8080 - NoMachine desktop
   
