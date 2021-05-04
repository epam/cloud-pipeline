param (
    $Command
)

######################################################
Write-Host "Init default variables if they are not set explicitly"
Write-Host "-"
######################################################

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

# Setup run directory
if ([string]::IsNullOrEmpty($env:RUN_DIR) -or -not(Test-Path -Path $env:RUN_DIR -IsValid)) {
    $env:RUN_DIR="$env:RUNS_ROOT\$env:PIPELINE_NAME-$env:RUN_ID"
    Write-Host "RUN_DIR is not defined, setting to $env:RUN_DIR"
}

if (-not(Test-Path -Path $env:RUN_DIR)) {
    Write-Host "Creating default run directory at $env:RUN_DIR"
    New-Item -Path $env:RUN_DIR -ItemType "Directory" -Force
}

# Setup common code directory
if ([string]::IsNullOrEmpty($env:COMMON_REPO_DIR) -or -not(Test-Path -Path $env:COMMON_REPO_DIR -IsValid)) {
    $env:COMMON_REPO_DIR="$env:RUN_DIR\CommonRepo"
    Write-Host "COMMON_REPO_DIR is not defined, setting to $env:COMMON_REPO_DIR"
}

if (-not(Test-Path -Path $env:COMMON_REPO_DIR)) {
    Write-Host "Creating default common code directory at $env:COMMON_REPO_DIR"
    New-Item -Path $env:COMMON_REPO_DIR -ItemType "Directory" -Force
}

# Setup pipe directory
if ([string]::IsNullOrEmpty($env:PIPE_DIR) -or -not(Test-Path -Path $env:PIPE_DIR -IsValid)) {
    $env:PIPE_DIR="$env:RUN_DIR\pipe"
    Write-Host "PIPE_DIR is not defined, setting to $env:PIPE_DIR"
}

if (-not(Test-Path -Path $env:PIPE_DIR)) {
    Write-Host "Creating default pipe directory at $env:PIPE_DIR"
    New-Item -Path $env:PIPE_DIR -ItemType "Directory" -Force
}

# Setup analysis directory
if ([string]::IsNullOrEmpty($env:ANALYSIS_DIR) -or -not(Test-Path -Path $env:ANALYSIS_DIR -IsValid)) {
    $env:ANALYSIS_DIR="$env:RUN_DIR\analysis"
    Write-Host "ANALYSIS_DIR is not defined, setting to $env:ANALYSIS_DIR"
}

if (-not(Test-Path -Path $env:ANALYSIS_DIR)) {
    Write-Host "Creating default analysis directory at $env:ANALYSIS_DIR"
    New-Item -Path $env:ANALYSIS_DIR -ItemType "Directory" -Force
}

Write-Host "------"
Write-Host ""
######################################################

######################################################
Write-Host "Installing pipeline packages and code"
Write-Host "-"
######################################################

if ([string]::IsNullOrEmpty($env:CP_PIPE_COMMON_ENABLED) -or $env:CP_PIPE_COMMON_ENABLED -ne "true") {
    Invoke-WebRequest "${env:DISTRIBUTION_URL}pipe-common.tar.gz" -Outfile "$env:COMMON_REPO_DIR\pipe-common.tar.gz"
    tar -xf "$env:COMMON_REPO_DIR\pipe-common.tar.gz" -C $env:COMMON_REPO_DIR
}

# Init path for powershell scripts from common repository
if (Test-Path -Path "$env:COMMON_REPO_DIR\powershell") {
    & "$env:COMMON_REPO_DIR\powershell\AddToPath.ps1" -AppendingPath $env:COMMON_REPO_DIR\powershell
}

# Resolve run's node ip
$env:NODE_IP = (RequestApi -HttpMethod "GET" -ApiMethod "run/$env:RUN_ID").instance.nodeIP

# Init path for powershell scripts from common repository within node
if (Test-Path -Path "$env:COMMON_REPO_DIR\powershell") {
    ExecuteWithinNode -Command "$env:COMMON_REPO_DIR\powershell\AddToPath.ps1 -AppendingPath $env:COMMON_REPO_DIR\powershell"
}

# Install pipe CLI
if ([string]::IsNullOrEmpty($env:CP_PIPE_CLI_ENABLED) -or $env:CP_PIPE_CLI_ENABLED -ne "true") {
    Invoke-WebRequest "${env:DISTRIBUTION_URL}pipe.zip" -Outfile "$env:PIPE_DIR\pipe.zip"
    Expand-Archive -Path "$env:PIPE_DIR\pipe.zip" -DestinationPath $(Split-Path -Path $env:PIPE_DIR)
}

# Init path for pipe cli
if (Test-Path -Path "$env:PIPE_DIR\pipe.exe") {
    AddToPath.ps1 -AppendingPath $env:PIPE_DIR
    ExecuteWithinNode -Command "AddToPath.ps1 -AppendingPath $env:PIPE_DIR"
}

# Configure pipe cli within node
ExecuteWithinNode -Command "pipe configure --auth-token '$env:API_TOKEN' --api '$env:API' --timezone local --proxy pac"

Write-Host "------"
Write-Host ""
######################################################

######################################################
Write-Host "Configure owner account"
Write-Host "-"
if (-not([string]::IsNullOrEmpty($env:OWNER))) {
    $env:OWNER_PASSWORD = New-Guid
    ExecuteWithinNode -Command "$env:COMMON_REPO_DIR\powershell\AddUser.ps1 -UserName $env:OWNER -UserPassword $env:OWNER_PASSWORD"
} else {
    Write-Host "OWNER is not set - skipping owner account configuration"
}
Write-Host "------"
Write-Host ""
######################################################

######################################################
Write-Host "Executing task"
Write-Host "-"
######################################################

Set-Location -Path $env:ANALYSIS_DIR
Write-Host "CWD is now at $env:ANALYSIS_DIR"

PipeLogSuccess -Text "Environment initialization finished" -Task "InitializeEnvironment"

try {
    . "$env:HOST_ROOT\NodeEnv.ps1"
    & $Command -ErrorAction Stop
    $CP_EXEC_RESULT = 0
} catch {
    Write-Error "$_"
    $CP_EXEC_RESULT = 1
}
Write-Host "------"
Write-Host ""
######################################################

######################################################
Write-Host "Finalizing execution"
Write-Host "-"
######################################################

Write-Host "Exiting with $CP_EXEC_RESULT"
Exit $CP_EXEC_RESULT
######################################################
