# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

param (
  [switch] $Install = $false
)

function CreateDirIfRequired($Path) {
    if (-not(Test-Path "$Path")) {
        New-Item -Path "$Path" -ItemType "Directory" -Force
    }
}

function RemoveDirIfExists($Path) {
    if (Test-Path "$Path") {
        Remove-Item -Path "$Path" -Recurse -Force
    }
}

function RemoveFileIfExists($Path) {
    if (Test-Path "$Path") {
        Remove-Item -Path "$Path" -Force
    }
}

if ($Install) {
    Write-Host "Installing data transfer service..."

    Write-Host "Checking required environment variables..."
    if (-not("$env:API" -and "$env:API_TOKEN" -and "$env:DISTRIBUTION_URL" -and "$env:DTS_NAME" -and "$env:DTS_DIR" -and "$env:CP_API_JWT_KEY_PUBLIC")) {
        Write-Host "Please set all the required environment variables and restart the installation: API, API_TOKEN, DISTRIBUTION_URL, DTS_NAME, DTS_DIR and CP_API_JWT_KEY_PUBLIC."
        Exit
    }

    Write-Host "Changing working directory..."
    Set-Location -Path "$env:DTS_DIR"

    Write-Host "Persisting environment..."
    @"
`$env:API = "$env:API"
`$env:API_TOKEN = "$env:API_TOKEN"
`$env:DISTRIBUTION_URL = "$env:DISTRIBUTION_URL"
`$env:DTS_DIR = "$env:DTS_DIR"
`$env:APP_HOME = "$env:DTS_DIR\app"
`$env:JAVA_HOME = "$env:DTS_DIR\app\jre"
`$env:PIPE_DIR = "$env:DTS_DIR\pipe"
`$env:DTS_LOGS_DIR = "$env:DTS_DIR\logs"
`$env:DTS_LAUNCHER_LOG_PATH = "$env:DTS_LOGS_DIR\launcher.log"
`$env:DTS_RESTART_DELAY_SECONDS = "10"
`$env:DTS_LAUNCHER_PATH = "$env:DTS_DIR\DeployDts.ps1"
`$env:DTS_DISTRIBUTION_URL = "$env:DISTRIBUTION_URL/data-transfer-service-windows.zip"
`$env:DTS_DISTRIBUTION_PATH = "$env:DTS_DIR\data-transfer-service-windows.zip"
`$env:PIPE_DISTRIBUTION_URL = "$env:DISTRIBUTION_URL/pipe.zip"
`$env:PIPE_DISTRIBUTION_PATH = "$env:DTS_DIR\pipe.zip"
`$env:CP_API_URL = "$env:API"
`$env:CP_API_JWT_TOKEN = "$env:API_TOKEN"
`$env:CP_API_JWT_KEY_PUBLIC = "$env:API_PUBLIC_KEY"
`$env:DTS_LOCAL_NAME = "$env:DTS_NAME"
"@ | Out-File -FilePath Environment.ps1 -Encoding ascii -Force -ErrorAction Stop

    Write-Host "Loading environment..."
    . .\Environment.ps1

    Write-Host "Creating scheduled task if it doesn't exist..."
    if (Get-ScheduledTask "CloudPipelineDTS" -ErrorAction SilentlyContinue) {
        Write-Host "Scheduled task already exists."
    } else {
        try {
            Write-Host "Creating scheduled task..."
            $action = New-ScheduledTaskAction -Execute "powershell.exe" `
                                              -Argument "-file `"$env:DTS_LAUNCHER_PATH`"" `
                                              -WorkingDirectory "$env:DTS_DIR"
            $trigger = New-ScheduledTaskTrigger -AtStartup
            $principal = New-ScheduledTaskPrincipal -UserId "SYSTEM"
            $settings = New-ScheduledTaskSettingsSet -Compatibility Win8
            $settings.ExecutionTimeLimit = "PT0S"
            $task = New-ScheduledTask -Action $action `
                                      -Principal $principal `
                                      -Trigger $trigger `
                                      -Settings $settings
            Register-ScheduledTask -TaskName "CloudPipelineDTS" -InputObject $task -Force -ErrorAction Stop
            Write-Host "Scheduled task was created successfully."
        } catch {
            Write-Host "Scheduled task creation has failed: $_"
            Write-Host "Please send all the logs above to Cloud Pipeline Support Team."
            Exit
        }
    }

    Write-Host "Starting scheduled task..."
    try {
        Start-ScheduledTask -TaskName "CloudPipelineDTS" -ErrorAction Stop
        Write-Host "Scheduled task started successfully."
    } catch {
        Write-Host "Scheduled task starting has failed: $_"
        Write-Host "Please send all the logs above to Cloud Pipeline Support Team."
    }

    Exit
}

Write-Host "Loading environment..."
. .\Environment.ps1

Write-Host "Changing working directory..."
Set-Location -Path "$env:DTS_DIR"

Write-Host "Creating system directories..."
CreateDirIfRequired -Path "$env:DTS_LOGS_DIR"

Write-Host "Starting logs capturing..."
Start-Transcript -Path "$env:DTS_LAUNCHER_LOG_PATH" -Append

Write-Host "Importing libraries..."
Add-Type -AssemblyName System.IO.Compression.FileSystem

While ($True) {
    try {
        Write-Host "Starting cycle at $((Get-Date).ToString("u"))..."

        Write-Host "Loading environment..."
        . .\Environment.ps1

        Write-Host "Stopping existing data transfer service processes..."
        $processes = Get-WmiObject win32_process -Filter "name like '%java%'"
        foreach($process in $processes) {
            $processId = $process.ProcessId
            $processCommand = $process.CommandLine
            if ("data-transfer-service" -in $processCommand) {
                Write-Host "Stopping existing data transfer service process #$processId..."
                Stop-Process -Id $processId -Force
            }
        }

        Write-Host "Removing existing data transfer service distribution..."
        RemoveFileIfExists -Path "$env:DTS_DISTRIBUTION_PATH"

        Write-Host "Removing existing data transfer service directory..."
        RemoveDirIfExists "$env:APP_HOME"

        Write-Host "Downloading data transfer service distribution..."
        Invoke-WebRequest "$env:DTS_DISTRIBUTION_URL" -OutFile "$env:DTS_DISTRIBUTION_PATH" -ErrorAction Stop

        Write-Host "Unpacking data transfer service distribution..."
        [System.IO.Compression.ZipFile]::ExtractToDirectory("$env:DTS_DISTRIBUTION_PATH", "$env:APP_HOME")

        Write-Host "Removing existing pipe distribution..."
        RemoveFileIfExists "$env:PIPE_DISTRIBUTION_PATH"

        Write-Host "Removing existing pipe directory..."
        RemoveDirIfExists "$env:PIPE_DIR"

        Write-Host "Downloading pipe distribution..."
        Invoke-WebRequest "$env:PIPE_DISTRIBUTION_URL" -OutFile "$env:PIPE_DISTRIBUTION_PATH" -ErrorAction Stop

        Write-Host "Unpacking pipe distribution..."
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        [System.IO.Compression.ZipFile]::ExtractToDirectory("$env:PIPE_DISTRIBUTION_PATH", "$env:PIPE_DIR")

        Write-Host "Launching data transfer service..."
        & "$env:APP_HOME\bin\dts.bat" >$null 2>&1
        Write-Host "Data transfer service has exited."

        Write-Host "Finishing cycle at $((Get-Date).ToString("u"))..."
    } catch {
        Write-Host "Finishing cycle at $((Get-Date).ToString("u")) with error: $_"
    }

    Write-Host "Waiting for $env:DTS_RESTART_DELAY_SECONDS seconds before proceeding..."
    Start-Sleep -Seconds "$env:DTS_RESTART_DELAY_SECONDS"
}
