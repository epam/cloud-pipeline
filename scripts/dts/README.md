# Data Transfer Service Launcher

Launcher script allows deploying Data Transfer Service for local directories' synchronisation to Windows hosts.
It handles Data Transfer Service failures and host restarts allowing to maintain continuous data synchronisation process.

# Deploy

To deploy Data Transfer Service using the launcher script the following Powershell snippet shall be executed.
Notice that `API`, `API_TOKEN` and `API_PUBLIC_KEY` environment variable values have to be set.

```powershell
$env:API = "{PUT REST API URL HERE}"
$env:API_TOKEN = "{PUT REST API TOKEN HERE}"
$env:API_PUBLIC_KEY = "{PUT REST API PUBLIC KEY}"
$env:DISTRIBUTION_URL = "$env:API" -replace "/restapi/",""
$env:DTS_DIR = "$env:ProgramFiles\CloudPipeline\DTS"
$env:DTS_NAME = hostname
if (-not(Test-Path "$env:DTS_DIR")) { New-Item -Path "$env:DTS_DIR" -ItemType "Directory" -Force }
Invoke-WebRequest "$env:DISTRIBUTION_URL/DeployDts.ps1" -OutFile "$env:DTS_DIR\DeployDts.ps1"
& "$env:DTS_DIR\DeployDts.ps1" -Install
```
