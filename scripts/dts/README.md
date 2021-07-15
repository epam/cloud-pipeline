# Data Transfer Service Launcher

Launcher script allows deploying Data Transfer Service for local directories' synchronisation to Windows hosts.
It handles Data Transfer Service failures and host restarts allowing to maintain continuous data synchronisation process.

# Deploy

To deploy dts using the launcher script the Powershell commands below can be used.
Notice that `API`, `API_TOKEN` and `API_PUBLIC_KEY` environment variables have to be set.

```powershell
$env:API = "{PUT REST API URL HERE}"
$env:API_TOKEN = "{PUT REST API TOKEN HERE}"
$env:API_PUBLIC_KEY = "{PUT REST API PUBLIC KEY}"
$env:DISTRIBUTION_URL = "$env:API" -replace "/restapi/",""
$env:DTS_DIR = "$env:ProgramFiles\CloudPipeline\DTS"
$env:DTS_NAME = hostname
if (-not(Test-Path "$env:DTS_DIR")) { New-Item -Path "$env:DTS_DIR" -ItemType "Directory" -Force }
Invoke-WebRequest "$env:DISTRIBUTION_URL/DeployDts.ps1" -OutFile "$env:DTS_DIR\DeployDts.ps1"
Set-ExecutionPolicy Unrestricted -Scope Process
& "$env:DTS_DIR\DeployDts.ps1" -Install
```

# Configure

To register new dts the command below can be used. Notice that DTS name should be the same as in the _Deploy_ step.

```bash
DTS_NAME="{PUT DTS HOST NAME HERE}"
pipe dts create --name "$DTS_NAME" \
                --url "$DTS_NAME" \
                --prefix "$DTS_NAME"
```

To synchronise some local directory as well as dts logs directory every midnight the command below can be used.

```bash
DTS_NAME="{PUT DTS HOST NAME HERE}"
pipe dts preferences update "$DTS_NAME" -p 'dts.local.sync.rules=[{
                                                "source": "c:\\local\\path\\to\\source\\directory",
                                                "destination": "s3://data/storage/path/to/destination/directory",
                                                "cron": "0 0/1 * ? * *"
                                            }, {
                                                "source": "c:\\Program Files\\CloudPipeline\\DTS\\logs",
                                                "destination": "s3://data/storage/path/to/logs/directory",
                                                "cron": "0 0/1 * ? * *"
                                            }]'
```

To restart dts the command below can be used.

```bash
DTS_NAME="{PUT DTS HOST NAME HERE}"
pipe dts preferences update "$DTS_NAME" -p 'dts.restart.force=true'
```
