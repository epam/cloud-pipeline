# VM monitoring service

`VMMonitor` is a service for tracking virtual machines in the Cloud provider and verifying that VMs are monitored correctly by Cloud Pipeline API.

### Implementation details 
Service is developed as Spring Boot application with a scheduled task which is invoked according to cron expression. 

The following algorithm is used to check VM and Nodes status:
- Fetch registered `Cloud regions` from Cloud Pipeline API
- Fetch running VMs corresponding to each `Cloud regions`
- For each VM check that corresponding `Node` is registered in Cloud Pipeline k8s cluster (VM and node are matched by private IP address)
- Additionally service checks if VM is associated with any `PipelineRun` via `Name` tag (configurable), if corresponding `PipelineRun` exists and it is in `RUNNING` status, VM is considered healthy. This check is added mostly for cases when VM is created, but not yet registered in k8s cluster during `NodeInitialization` task.
- If matching `Node` is not found `MISSING-NODE` notification is sent
- If matching `Node` exists, service checks that labels required for node management are present (`runid` label by default)
- If some of required labels is absent, `MISSING-LABELS` notification is sent
