# Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

function Log($Message) {
    Write-Host "[$((Get-Date).ToString("u"))] $Message"
}

function CreateDirIfRequired($Path) {
    if (-not(Test-Path "$Path")) {
        New-Item -Path "$Path" -ItemType "Directory" -Force -ErrorAction Stop
    }
}

function RemoveDirIfExists($Path) {
    if (Test-Path "$Path") {
        Remove-Item -Path "$Path" -Recurse -Force -ErrorAction Stop
    }
}

function RemoveFileIfExists($Path) {
    if (Test-Path "$Path") {
        Remove-Item -Path "$Path" -Force -ErrorAction Stop
    }
}

$APP_DIR = "$env:APP_DIR"
$APP_DISTRIBUTION_URL = "$env:APP_DISTRIBUTION_URL"

Log "Checking required environment variables..."
if (-not("$APP_DIR" -and "$APP_DISTRIBUTION_URL")) {
    Log "The following environment variables are missing: APP_DIR, APP_DISTRIBUTION_URL."
    Exit
}

$APP_LOGS_DIR = "$APP_DIR\logs"
$APP_BACKUP_DIR = "$APP_DIR\backup"
$APP_DISTRIBUTION_DIR = "$APP_DIR\distribution"
$APP_DISTRIBUTION_PATH = "$APP_DIR\distribution.zip"
$APP_SECURE_PATHS = "logs","backup","distribution","distribution.zip","settings.json"
$APP_BACKUP_SECURE_PATHS = "logs","distribution","distribution.zip","settings.json"
$APP_DISTRIBUTION_SECURE_PATHS = "logs","backup","settings.json"
$APP_START_DELAY_SECONDS = "5"
$APP_FINISH_DELAY_SECONDS = "1"
$APP_RESTART_DELAY_SECONDS = "1"
$APP_RESTART_ATTEMPTS = "5"

Log "Creating system directories..."
CreateDirIfRequired -Path "$APP_LOGS_DIR"
CreateDirIfRequired -Path "$APP_BACKUP_DIR"
CreateDirIfRequired -Path "$APP_DISTRIBUTION_DIR"

Start-Transcript -Path "$APP_LOGS_DIR\updater.log" -Append
Log "Starting logs capturing..."

Log "Importing libraries..."
Add-Type -AssemblyName System.IO.Compression.FileSystem

Log "Configuring libraries..."
Add-Type @"
using System.Net;
using System.Security.Cryptography.X509Certificates;
public class TrustAllCertsPolicy : ICertificatePolicy {
    public bool CheckValidationResult(
        ServicePoint srvPoint, X509Certificate certificate,
        WebRequest request, int certificateProblem) {
            return true;
        }
 }
"@
[System.Net.ServicePointManager]::CertificatePolicy = New-Object TrustAllCertsPolicy
$ProgressPreference = "SilentlyContinue"

Log "Changing working directory to $APP_LOGS_DIR..."
Set-Location -Path "$APP_LOGS_DIR" -ErrorAction Stop

Log "Backuping existing service directory..."
Copy-Item -Recurse -Path "$APP_DIR\*" -Destination "$APP_BACKUP_DIR\" -Exclude $APP_SECURE_PATHS -Force -ErrorAction Stop

Log "Resolving service executables..."
$APP_EXEC_NAME = (Get-ChildItem -Path "$APP_DIR" -Filter "*.exe" -ErrorAction Stop).Name
$APP_EXEC_PATH = "$APP_DIR\$APP_EXEC_NAME"
$APP_PROC_NAME = "$APP_EXEC_NAME"
$APP_PROC_CMD = "$APP_EXEC_NAME"

foreach($attempt in 1..$APP_RESTART_ATTEMPTS) {
    try {
        Log "Trying updating (#$attempt)..."

        Log "Stopping existing service processes..."
        $processes = Get-WmiObject win32_process -Filter "name like `"%$APP_PROC_NAME%`""
        foreach($process in $processes) {
            $processId = $process.ProcessId
            $processCommand = $process.CommandLine

            try {
                Log "Checking if existing service process #$processId still exists..."
                Get-Process -Id $processId -ErrorAction Stop
            } catch {
                Log "Skipping existing service process #$processId because it has already finished..."
                continue
            }

            if ($processCommand -NotMatch "$APP_PROC_CMD") {
                Log "Skipping existing non service process #$processId..."
                continue
            }

            Log "Stopping existing service process #$processId..."
            Stop-Process -Id $processId -Force -ErrorAction Stop

            Log "Waiting for $APP_FINISH_DELAY_SECONDS seconds before proceeding..."
            Start-Sleep -Seconds "$APP_FINISH_DELAY_SECONDS"
        }

        Log "Removing existing service distribution..."
        RemoveFileIfExists -Path "$APP_DISTRIBUTION_PATH"

        Log "Removing existing service directory..."
        RemoveDirIfExists -Path "$APP_DISTRIBUTION_DIR"

        Log "Downloading service distribution..."
        Invoke-WebRequest "$APP_DISTRIBUTION_URL" -OutFile "$APP_DISTRIBUTION_PATH" -ErrorAction Stop

        Log "Unpacking service distribution..."
        [System.IO.Compression.ZipFile]::ExtractToDirectory("$APP_DISTRIBUTION_PATH", "$APP_DISTRIBUTION_DIR")
        $APP_DISTRIBUTION_TMP_DIR = (Get-ChildItem -Path "$APP_DISTRIBUTION_DIR").FullName
        Move-Item -Path "$APP_DISTRIBUTION_TMP_DIR\*" -Destination "$APP_DISTRIBUTION_DIR" -Force -ErrorAction Stop
        Remove-Item -Path "$APP_DISTRIBUTION_TMP_DIR" -Force -ErrorAction Stop

        Log "Applying distribution service directory..."
        Get-ChildItem -Path "$APP_DIR" -Exclude $APP_SECURE_PATHS `
            | Remove-Item -Recurse -Force -ErrorAction Stop
        Get-ChildItem -Path "$APP_DISTRIBUTION_DIR" -Exclude $APP_DISTRIBUTION_SECURE_PATHS `
            | ForEach-Object { Move-Item -Path $_.FullName -Destination "$APP_DIR\" -Force -ErrorAction Stop }

        Log "Launching updated service..."
        $env:APP_EXEC_PATH = $APP_EXEC_PATH
        Start-Job {
            & "$env:APP_EXEC_PATH" >$null 2>&1
        }
        Log "The updated service has been launched."

        Log "Waiting for $APP_START_DELAY_SECONDS seconds before proceeding..."
        Start-Sleep -Seconds "$APP_START_DELAY_SECONDS"

        Log "Update has been successfull."
        Break
    } catch {
        Log "Update has failed on #$attempt attempt with error: $_"

        if ($attempt -eq $APP_RESTART_ATTEMPTS) {
            Log "Update is being reverted after $APP_RESTART_ATTEMPTS attempts."

            if (Test-Path "$APP_BACKUP_DIR") {
                Log "Reverting to backup service directory..."
                Get-ChildItem -Path "$APP_DIR" -Exclude $APP_SECURE_PATHS `
                    | Remove-Item -Force -Recurse -ErrorAction Continue
                Get-ChildItem -Path "$APP_BACKUP_DIR" -Exclude $APP_BACKUP_SECURE_PATHS `
                    | ForEach-Object { Move-Item -Path $_.FullName -Destination "$APP_DIR\" -Force -ErrorAction Continue }

                Log "Launching backuped service..."
                $env:APP_EXEC_PATH = $APP_EXEC_PATH
                Start-Job {
                    & "$env:APP_EXEC_PATH" >$null 2>&1
                }
                Log "The backuped service has been launched."

                Log "Waiting for $APP_START_DELAY_SECONDS seconds before proceeding..."
                Start-Sleep -Seconds "$APP_START_DELAY_SECONDS"
            }

            Log "Update has been aborted."
        }
    }

    Log "Waiting for $APP_RESTART_DELAY_SECONDS seconds before proceeding..."
    Start-Sleep -Seconds "$APP_RESTART_DELAY_SECONDS"
}

Log "Stopping logs capturing..."
Stop-Transcript

RemoveFileIfExists -Path "$APP_DISTRIBUTION_PATH"
RemoveDirIfExists -Path "$APP_DISTRIBUTION_DIR"
RemoveDirIfExists -Path "$APP_BACKUP_DIR"
