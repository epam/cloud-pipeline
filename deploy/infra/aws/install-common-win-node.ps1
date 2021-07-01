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

function OpenPortIfRequired($Port) {
    $FilewallRuleName="Cloud Pipeline Inbound $Port Port"
    try {
        Get-NetFirewallRule -DisplayName $FilewallRuleName -ErrorAction Stop
    } catch {
        Write-Host "Opening port $Port..."
        New-NetFirewallRule -DisplayName $FilewallRuleName `
                        -Direction Inbound `
                        -LocalPort $Port `
                        -Protocol TCP `
                        -Action Allow
    }
}

function NewDirIfRequired($Path) {
    if (-not(Test-Path $Path)) {
        New-Item -Path $Path -ItemType "Directory" -Force
    }
}

function InstallNoMachineIfRequired {
    $restartRequired=$false
    $nomachineInstalled = Get-Service -Name nxservice `
    | Measure-Object `
    | ForEach-Object { $_.Count -gt 0 }
    if (-not($nomachineInstalled)) {
        Write-Host "Installing NoMachine..."
        Invoke-WebRequest "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/nomachine/nomachine_7.6.2_4.exe" -Outfile .\nomachine.exe
        cmd /c "nomachine.exe /verysilent"
        $restartRequired=$true
    }
    return $restartRequired
}

function InstallOpenSshServerIfRequired {
    $restartRequired=$false
    $openSshServerInstalled = Get-WindowsCapability -Online `
        | Where-Object { $_.Name -match "OpenSSH\.Server*" } `
        | ForEach-Object { $_.State -eq "Installed" }
    if (-not($openSshServerInstalled)) {
        Write-Host "Installing OpenSSH server..."
        Add-WindowsCapability -Online -Name OpenSSH.Server~~~~0.0.1.0
        New-ItemProperty -Path "HKLM:\SOFTWARE\OpenSSH" -Name DefaultShell -Value "C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe" -PropertyType String -Force
        $restartRequired=$false
    }
    return $restartRequired
}

function InstallWebDAVIfRequired {
    $restartRequired=$false
    $webDAVInstalled = Get-WindowsFeature `
        | Where-Object { $_.Name -match "WebDAV-Redirector" } `
        | ForEach-Object { $_.InstallState -eq "Installed" }
    if (-not($webDAVInstalled)) {
        Write-Host "Installing WebDAV..."
        Install-WindowsFeature WebDAV-Redirector
        $restartRequired=$true
    }
    return $restartRequired
}

function InstallPGinaIfRequired {
    $restartRequired=$false
    $pGinaInstalled = Get-Service -Name pgina `
        | Measure-Object `
        | ForEach-Object { $_.Count -gt 0 }
    if (-not($pGinaInstalled)) {
        Write-Host "Installing pGina..."
        Invoke-WebRequest "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/pgina/pGina-3.2.4.0-setup.exe" -OutFile "pGina-3.2.4.0-setup.exe"
        Invoke-WebRequest "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/pgina/vcredist_x64.exe" -OutFile "vcredist_x64.exe"
        .\pGina-3.2.4.0-setup.exe /S /D=C:\Program Files\pGina
        WaitForProcess -ProcessName "pGina-3.2.4.0-setup"
        .\vcredist_x64.exe /quiet
        WaitForProcess -ProcessName "vcredist_x64"
        Invoke-WebRequest "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/pgina/pGina.Plugin.AuthenticateAllPlugin.dll" -OutFile "C:\Program Files\pGina\Plugins\Contrib\pGina.Plugin.AuthenticateAllPlugin.dll"
        Set-ItemProperty -Path "HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Authentication\Credential Providers\{d0befefb-3d2c-44da-bbad-3b2d04557246}" -Name "Disabled" -Type "DWord" -Value "1"
        Set-ItemProperty -Path "HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Authentication\Credential Providers\{d0befefb-3d2c-44da-bbad-3b2d04557246}" -Name "Disabled" -Type "DWord" -Value "1"
        $restartRequired=$true
    }
    return $restartRequired
}

function InstallDockerIfRequired {
    $restartRequired=$false
    $dockerInstalled = Get-Service -Name docker `
        | Measure-Object `
        | ForEach-Object { $_.Count -gt 0 }
    if (-not ($dockerInstalled)) {
        Get-PackageProvider -Name NuGet -ForceBootstrap
        Install-Module -Name DockerMsftProvider -Repository PSGallery -Force
        Install-Package -Name docker -ProviderName DockerMsftProvider -Force -RequiredVersion 19.03.14
        $restartRequired=$true
    }
    return $restartRequired
}

function WaitForProcess($ProcessName) {
    while ($True) {
        $Process = Get-Process | Where-Object {$_.Name -contains $ProcessName}
        If ($Process) {
            Start-Sleep -Seconds 1
        } else {
            break
        }
    }
}

function InstallPythonIfRequired($PythonDir) {
    if (-not (Test-Path "$PythonDir")) {
        Write-Host "Installing python..."
        Invoke-WebRequest -Uri "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/python/3/python-3.8.9-amd64.exe" -OutFile "$workingDir\python-3.8.9-amd64.exe"
        & "$workingDir\python-3.8.9-amd64.exe" /quiet TargetDir=$PythonDir InstallAllUsers=1 PrependPath=1
        WaitForProcess -ProcessName "python-3.8.9-amd64"
    }
}

function InstallChromeIfRequired {
    if (-not (Test-Path "C:\Program Files\Google\Chrome\Application\chrome.exe")) {
        Write-Host "Installing chrome..."
        Invoke-WebRequest "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/chrome/ChromeSetup.exe" -Outfile $workingDir\ChromeSetup.exe
        & $workingDir\ChromeSetup.exe /silent /install
        WaitForProcess -ProcessName "ChromeSetup"
    }
}

function GenerateSshKeys($Path) {
    NewDirIfRequired -Path "$Path\.ssh"
    if (!(Test-Path "$Path\.ssh\id_rsa")) {
        cmd /c "ssh-keygen.exe -t rsa -N """" -f ""$Path\.ssh\id_rsa"""
    }
}

function DownloadSigWindowsToolsIfRequired {
    if (-not(Test-Path .\sig-windows-tools)) {
        NewDirIfRequired -Path .\sig-windows-tools
        Invoke-WebRequest "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/kube/1.15.4/win/sig-windows-tools-00012ee6d171b105e7009bff8b2e42d96a45426f.zip" -Outfile .\sig-windows-tools.zip
        tar -xvf .\sig-windows-tools.zip --strip-components=1 -C sig-windows-tools
    }
}

function InitSigWindowsToolsConfigFile($KubeHost, $KubeToken, $KubeCertHash, $KubeDir, $Interface) {
    $configfile = @"
{
    "Cri" : {
        "Name" : "dockerd",
        "Images" : {
            "Pause" : "mcr.microsoft.com/k8s/core/pause:1.2.0",
            "Nanoserver" : "mcr.microsoft.com/windows/nanoserver:1809",
            "ServerCore" : "mcr.microsoft.com/windows/servercore:ltsc2019"
        }
    },
    "Cni" : {
        "Name" : "flannel",
        "Source" : [{
            "Name" : "flanneld",
            "Url" : "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/kube/1.15.4/win/flanneld.exe"
            }
        ],
        "Plugin" : {
            "Name": "vxlan"
        },
        "InterfaceName" : "$Interface"
    },
    "Kubernetes" : {
        "Source" : {
            "Release" : "1.15.4",
            "Url" : "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/kube/1.15.4/win/kubernetes-node-windows-amd64.tar.gz"
        },
        "ControlPlane" : {
            "IpAddress" : "$KubeHost",
            "Username" : "root",
            "KubeadmToken" : "$KubeToken",
            "KubeadmCAHash" : "sha256:$KubeCertHash"
        },
        "KubeProxy" : {
            "Gates" : "WinOverlay=true"
        },
        "Network" : {
            "ServiceCidr" : "10.96.0.0/12",
            "ClusterCidr" : "10.244.0.0/16"
        }
    },
    "Install" : {
        "Destination" : "$($KubeDir -replace "\\","\\")"
    }
}
"@
    $configfile|Out-File -FilePath .\Kubeclustervxlan.json -Encoding ascii -Force
}

function InstallKubeUsingSigWindowsToolsIfRequired($KubeDir) {
    $kubernetesInstalled = Get-ChildItem $KubeDir `
        | Measure-Object `
        | ForEach-Object { $_.Count -gt 0 }
    if (-not($kubernetesInstalled)) {
        Write-Host "Installing kubernetes using Sig Windows Tools..."
        .\sig-windows-tools\kubeadm\KubeCluster.ps1 -ConfigFile .\Kubeclustervxlan.json -install
    }
}

$interface = Get-NetAdapter | Where-Object { $_.Name -match "Ethernet \d+" } | ForEach-Object { $_.Name }

$homeDir = "$env:USERPROFILE"
$workingDir="c:\init"
$kubeDir="c:\ProgramData\Kubernetes"
$pythonDir = "c:\python"
$initLog="$workingDir\log.txt"

$instanceId = Invoke-RestMethod -uri http://169.254.169.254/latest/meta-data/instance-id
$region = Invoke-RestMethod -uri http://169.254.169.254/latest/dynamic/instance-identity/document | ForEach-Object { $_.region }
$env:AWS_ACCESS_KEY_ID = "{{AWS_ACCESS_KEY_ID}}"
$env:AWS_SECRET_ACCESS_KEY = "{{AWS_SECRET_ACCESS_KEY}}"
$env:AWS_DEFAULT_REGION = "$region"

Write-Host "Creating system directories..."
NewDirIfRequired -Path "$workingDir"
NewDirIfRequired -Path "$kubeDir"

Write-Host "Starting logs capturing..."
Start-Transcript -path $initLog -append

Write-Host "Changing working directory..."
Set-Location -Path "$workingDir"

Write-Host "Installing nomachine if required..."
InstallNoMachineIfRequired

Write-Host "Installing OpenSSH server if required..."
InstallOpenSshServerIfRequired

Write-Host "Installing WebDAV if required..."
InstallWebDAVIfRequired

Write-Host "Installing pGina if required..."
InstallPGinaIfRequired

Write-Host "Installing docker if required..."
$restartRequired = (InstallDockerIfRequired | Select-Object -Last 1) -or $restartRequired
Write-Host "Restart required: $restartRequired"

Write-Host "Restarting computer if required..."
if ($restartRequired) {
    Write-Host "Restarting computer..."
    Stop-Transcript
    Restart-Computer -Force
    Exit
}

Write-Host "Installing python if required..."
InstallPythonIfRequired -PythonDir $pythonDir

Write-Host "Installing chrome if required..."
InstallChromeIfRequired

Write-Host "Opening host ports..."
OpenPortIfRequired -Port 4000
OpenPortIfRequired -Port 8888

Write-Host "Generating temporary SSH keys..."
GenerateSshKeys -Path $homeDir

Write-Host "Downloading Sig Windows Tools if required..."
DownloadSigWindowsToolsIfRequired

Write-Host "Generating Sig Windows Tools dummy config file..."
InitSigWindowsToolsConfigFile -KubeHost "default" -KubeToken "default" -KubeCertHash "default" -KubeDir "$kubeDir" -Interface "$interface"

Write-Host "Installing kubernetes using Sig Windows Tools if required..."
InstallKubeUsingSigWindowsToolsIfRequired -KubeDir "$kubeDir"

Write-Host "Removing temporary SSH keys..."
Remove-Item -Recurse -Force "$homeDir\.ssh"

# todo: Remove once kubelet pulling issue is resolved.
#  See https://github.com/epam/cloud-pipeline/issues/1832#issuecomment-832841950
Write-Host "Prepulling Windows tool base docker image..."
docker pull python:3.8.9-windowsservercore

Write-Host "Scheduling instance initialization on next launch..."
C:\ProgramData\Amazon\EC2-Windows\Launch\Scripts\InitializeInstance.ps1 -Schedule

Write-Host "Labelling instance as done..."
New-EC2Tag -Resource "$instanceId" -Tag @{Key="userdata"; Value="done"}

Stop-Transcript
