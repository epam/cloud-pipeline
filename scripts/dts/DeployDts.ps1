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

function Log($Message) {
    Write-Host "[$((Get-Date).ToString("u"))] $Message"
}

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
    Log "Installing data transfer service..."

    Log "Checking required environment variables..."
    if (-not("$env:API" -and "$env:API_TOKEN" -and "$env:DISTRIBUTION_URL" -and "$env:DTS_NAME" -and "$env:DTS_DIR" -and "$env:API_PUBLIC_KEY")) {
        Log "Please set all the required environment variables and restart the installation: API, API_TOKEN, DISTRIBUTION_URL, DTS_NAME, DTS_DIR and API_PUBLIC_KEY."
        Exit
    }

    Log "Changing working directory..."
    Set-Location -Path "$env:DTS_DIR"

    Log "Persisting environment..."
    @"
`$env:API = "$env:API"
`$env:API_TOKEN = "$env:API_TOKEN"
`$env:DISTRIBUTION_URL = "$env:DISTRIBUTION_URL"
`$env:DTS_DIR = "$env:DTS_DIR"
`$env:APP_HOME = "$env:DTS_DIR\app"
`$env:JAVA_HOME = "$env:DTS_DIR\app\jre"
`$env:PIPE_DIR = "$env:DTS_DIR\pipe"
`$env:DTS_LOGS_DIR = "$env:DTS_DIR\logs"
`$env:DTS_LOCKS_DIR = "$env:DTS_DIR\locks"
`$env:DTS_LAUNCHER_LOG_PATH = "$env:DTS_DIR\logs\launcher.log"
`$env:DTS_RESTART_DELAY_SECONDS = "10"
`$env:DTS_FINISH_DELAY_SECONDS = "10"
`$env:DTS_RESTART_INTERVAL = "PT1M"
`$env:DTS_LAUNCHER_URL = "$env:DISTRIBUTION_URL/DeployDts.ps1"
`$env:DTS_LAUNCHER_PATH = "$env:DTS_DIR\DeployDts.ps1"
`$env:DTS_DISTRIBUTION_URL = "$env:DISTRIBUTION_URL/data-transfer-service-windows.zip"
`$env:DTS_DISTRIBUTION_PATH = "$env:DTS_DIR\data-transfer-service-windows.zip"
`$env:PIPE_DISTRIBUTION_URL = "$env:DISTRIBUTION_URL/pipe.zip"
`$env:PIPE_DISTRIBUTION_PATH = "$env:DTS_DIR\pipe.zip"
`$env:CP_API_URL = "$env:API"
`$env:CP_API_JWT_TOKEN = "$env:API_TOKEN"
`$env:CP_API_JWT_KEY_PUBLIC = "$env:API_PUBLIC_KEY"
`$env:DTS_LOCAL_NAME = "$env:DTS_NAME"
`$env:DTS_IMPERSONATION_ENABLED = "false"
`$env:DTS_PIPE_EXECUTABLE = "$env:DTS_DIR\pipe\pipe\pipe"
"@ | Out-File -FilePath Environment.ps1 -Encoding ascii -Force -ErrorAction Stop

    Log "Loading environment..."
    . .\Environment.ps1

    Log "Creating scheduled task if it doesn't exist..."
    if (Get-ScheduledTask "CloudPipelineDTS" -ErrorAction SilentlyContinue) {
        Log "Scheduled task already exists."
    } else {
        try {
            Log "Creating scheduled task..."
            $action = New-ScheduledTaskAction -Execute "powershell.exe" `
                                              -Argument "-executionpolicy bypass -file `"$env:DTS_LAUNCHER_PATH`"" `
                                              -WorkingDirectory "$env:DTS_DIR"
            $trigger = @(
                (New-ScheduledTaskTrigger -AtStartup),
                (New-ScheduledTaskTrigger -Once -At (Get-Date).Date)
            )
            $principal = New-ScheduledTaskPrincipal -UserId "SYSTEM"
            $settings = New-ScheduledTaskSettingsSet -Compatibility Win8
            $settings.ExecutionTimeLimit = "PT0S"
            $task = New-ScheduledTask -Action $action `
                                      -Principal $principal `
                                      -Trigger $trigger `
                                      -Settings $settings
            Register-ScheduledTask -TaskName "CloudPipelineDTS" -InputObject $task -Force -ErrorAction Stop
            $task = Get-ScheduledTask "CloudPipelineDTS"
            $task.Triggers[0].Repetition.Interval = "$env:DTS_RESTART_INTERVAL"
            $task.Triggers[1].Repetition.Interval = "$env:DTS_RESTART_INTERVAL"
            $task | Set-ScheduledTask -ErrorAction Stop
            Log "Scheduled task was created successfully."
        } catch {
            Log "Scheduled task creation has failed: $_"
            Log "Please send all the logs above to Cloud Pipeline Support Team."
            Exit
        }
    }

    Log "Starting scheduled task..."
    try {
        Start-ScheduledTask -TaskName "CloudPipelineDTS" -ErrorAction Stop
        Log "Scheduled task started successfully."
    } catch {
        Log "Scheduled task starting has failed: $_"
        Log "Please send all the logs above to Cloud Pipeline Support Team."
    }

    Exit
}

CreateDirIfRequired -Path .\logs
Start-Transcript -Path .\logs\launcher.log -Append
Log "Starting startup logs capturing..."

Log "Checking if environment script exists..."
if (-not(Test-Path .\Environment.ps1)) {
    Log "Environment script doesn't exist. Exiting..."
    Stop-Transcript
    Exit
}

Log "Loading environment..."
. .\Environment.ps1

Log "Changing working directory..."
Set-Location -Path "$env:DTS_DIR"

Log "Creating system directories..."
CreateDirIfRequired -Path "$env:DTS_LOGS_DIR"

Log "Stopping startup logs capturing..."
Stop-Transcript

Start-Transcript -Path "$env:DTS_LAUNCHER_LOG_PATH" -Append
Log "Starting logs capturing..."

Log "Importing libraries..."
Add-Type -AssemblyName System.IO.Compression.FileSystem

While ($True) {
    try {
        Log "Starting cycle..."

        Log "Stopping existing data transfer service processes..."
        $processes = Get-WmiObject win32_process -Filter "name like '%java%'"
        foreach($process in $processes) {
            $processId = $process.ProcessId
            $processCommand = $process.CommandLine
            if ($processCommand -Match "data-transfer-service") {
                Log "Stopping existing data transfer service process #$processId..."
                Stop-Process -Id $processId -Force -ErrorAction Stop

                Log "Waiting for $env:DTS_FINISH_DELAY_SECONDS seconds before proceeding..."
                Start-Sleep -Seconds "$env:DTS_FINISH_DELAY_SECONDS"
            }
        }

        Log "Removing existing data transfer service distribution..."
        RemoveFileIfExists -Path "$env:DTS_DISTRIBUTION_PATH"

        Log "Removing existing data transfer service directory..."
        RemoveDirIfExists "$env:APP_HOME"

        Log "Downloading data transfer service distribution..."
        Invoke-WebRequest "$env:DTS_DISTRIBUTION_URL" -OutFile "$env:DTS_DISTRIBUTION_PATH" -ErrorAction Stop

        Log "Unpacking data transfer service distribution..."
        [System.IO.Compression.ZipFile]::ExtractToDirectory("$env:DTS_DISTRIBUTION_PATH", "$env:APP_HOME")

        Log "Removing existing pipe distribution..."
        RemoveFileIfExists "$env:PIPE_DISTRIBUTION_PATH"

        Log "Removing existing pipe directory..."
        RemoveDirIfExists "$env:PIPE_DIR"

        Log "Downloading pipe distribution..."
        Invoke-WebRequest "$env:PIPE_DISTRIBUTION_URL" -OutFile "$env:PIPE_DISTRIBUTION_PATH" -ErrorAction Stop

        Log "Unpacking pipe distribution..."
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        [System.IO.Compression.ZipFile]::ExtractToDirectory("$env:PIPE_DISTRIBUTION_PATH", "$env:PIPE_DIR")

        Log "Launching data transfer service..."
        & "$env:APP_HOME\bin\dts.bat" >$null 2>&1
        Log "Data transfer service has exited."

        Log "Removing existing temporary data transfer service launcher..."
        RemoveFileIfExists ("$env:DTS_LAUNCHER_PATH" + ".new")

        Log "Downloading data transfer service launcher..."
        Invoke-WebRequest "$env:DTS_LAUNCHER_URL" -OutFile ("$env:DTS_LAUNCHER_PATH" + ".new") -ErrorAction Stop

        Log "Replacing existing data transfer service launcher..."
        Move-Item -Path ("$env:DTS_LAUNCHER_PATH" + ".new") -Destination "$env:DTS_LAUNCHER_PATH" -Force -ErrorAction Stop

        Log "Finishing cycle..."
        Break
    } catch {
        Log "Finishing cycle with error: $_"
    }

    Log "Waiting for $env:DTS_RESTART_DELAY_SECONDS seconds before proceeding..."
    Start-Sleep -Seconds "$env:DTS_RESTART_DELAY_SECONDS"
}

Log "Stopping logs capturing..."
Stop-Transcript
