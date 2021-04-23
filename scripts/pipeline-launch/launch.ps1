param ($Command)

function Request-Api($HttpMethod, $ApiMethod, $Body) {
    $headers = @{
        "Authorization" = "Bearer $env:API_TOKEN"
        "Content-Type" = "application/json"
    }
    $response = Invoke-RestMethod -Method $HttpMethod `
                                  -Uri "${env:API}${ApiMethod}" `
                                  -Body $Body `
                                  -Headers $headers
    if ($response.status -ne "OK") {
        Write-Host "ERROR: $($response.message)"
        return $null
    }
    return $response.payload
}

Write-Host "Init default variables if they are not set explicitly"
Write-Host "-"

$env:HOST_ROOT = "c:\host"
$env:RUNS_ROOT="c:\runs"

if ([string]::IsNullOrEmpty($env:RUN_ID)) {
    $env:RUN_ID="0"
    Write-Host "RUN_ID is not defined, setting to $env:RUN_ID"
}

if ([string]::IsNullOrEmpty($env:PIPELINE_NAME)) {
    $env:PIPELINE_NAME="DefaultPipeline"
    Write-Host "PIPELINE_NAME is not defined, setting to $env:PIPELINE_NAME"
}

if ([string]::IsNullOrEmpty($env:RUN_DIR) -or -not(Test-Path -Path $env:RUN_DIR -IsValid)) {
    $env:RUN_DIR="$env:RUNS_ROOT\$env:PIPELINE_NAME-$env:RUN_ID"
    Write-Host "RUN_DIR is not defined, setting to $env:RUN_DIR"
}

if (-not(Test-Path -Path $env:RUN_DIR)) {
    Write-Host "Creating default run directory at $env:RUN_DIR"
    New-Item -Path $env:RUN_DIR -ItemType "Directory" -Force
}

if ([string]::IsNullOrEmpty($env:COMMON_REPO_DIR) -or -not(Test-Path -Path $env:COMMON_REPO_DIR -IsValid)) {
    $env:COMMON_REPO_DIR="$env:RUN_DIR\CommonRepo"
    Write-Host "COMMON_REPO_DIR is not defined, setting to $env:COMMON_REPO_DIR"
}

if (-not(Test-Path -Path $env:COMMON_REPO_DIR)) {
    Write-Host "Creating default common code directory at $env:COMMON_REPO_DIR"
    New-Item -Path $env:COMMON_REPO_DIR -ItemType "Directory" -Force
}

Write-Host "Installing pipeline packages and code"
Write-Host "-"
Invoke-WebRequest "${env:DISTRIBUTION_URL}pipe-common.tar.gz" -Outfile "$env:COMMON_REPO_DIR\pipe-common.tar.gz"
tar -xf "$env:COMMON_REPO_DIR\pipe-common.tar.gz" -C $env:COMMON_REPO_DIR
Write-Host "------"

Write-Host "Resolving pipeline run node"
Write-Host "-"
$pipelineRun = Request-Api -HttpMethod "GET" -ApiMethod "run/$env:RUN_ID" -Body $null
$env:NODE_IP = $pipelineRun.instance.nodeIP
Write-Host "Resolved pipeline run node ($env:NODE_IP)."
Write-Host "------"

Write-Host "Configure owner account"
Write-Host "-"
if (-not([string]::IsNullOrEmpty($env:OWNER))) {
    $env:OWNER_PASSWORD = New-Guid
    ssh -i $env:HOST_ROOT\.ssh\id_rsa "Administrator@$env:nodeIp" powershell -Command @"
$env:COMMON_REPO_DIR\powershell\AddUser.ps1 -UserName $env:OWNER -UserPassword $env:OWNER_PASSWORD
"@
} else {
	Write-Host "OWNER is not set - skipping owner account configuration"
}
Write-Host "------"

Write-Host "Executing task"
Write-Host "-"
Request-Api -HttpMethod "POST" -ApiMethod "run/$env:RUN_ID/log" -Body @"
{
    "date": "$(Get-Date -Format "yyyy-MM-dd HH:mm:ss.fff")",
    "logText": "Environment initialization finished",
    "status": "SUCCESS",
    "taskName": "InitializeEnvironment"
}
"@
try {
    & $Command
    $CP_EXEC_RESULT = 0
} catch {
    Write-Error "$_"
    $CP_EXEC_RESULT = 1
}
Write-Host "------"

Write-Host "Finalizing execution"
Write-Host "-"

Write-Host "Exiting with $CP_EXEC_RESULT"
exit $CP_EXEC_RESULT
