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
        Invoke-WebRequest 'https://download.nomachine.com/download/7.4/Windows/nomachine_7.4.1_1.exe' -Outfile .\nomachine.exe
        cmd /c "nomachine.exe /verysilent"
        $restartRequired=$true
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
        Invoke-WebRequest -Uri "https://www.python.org/ftp/python/3.8.9/python-3.8.9-amd64.exe" -OutFile "$workingDir\python-3.8.9-amd64.exe"
        & "$workingDir\python-3.8.9-amd64.exe" /quiet TargetDir=$PythonDir InstallAllUsers=1 PrependPath=1
        WaitForProcess -ProcessName "python-3.8.9-amd64"
    }
}

function InstallChromeIfRequired {
    if (-not (Test-Path "C:\Program Files\Google\Chrome\Application\chrome.exe")) {
        Write-Host "Installing chrome..."
        Invoke-WebRequest 'https://dl.google.com/chrome/install/latest/chrome_installer.exe' -Outfile $workingDir\chrome_installer.exe
        & $workingDir\chrome_installer.exe /silent /install
        WaitForProcess -ProcessName "chrome_installer"
    }
}

function DownloadScrambleScriptIfRequired {
    if (-not(Test-Path .\scramble.exe)) {
        Invoke-WebRequest 'https://s3.amazonaws.com/cloud-pipeline-oss-builds/tools/nomachine/scramble.exe' -Outfile .\scramble.exe
    }
}

function InstallOpenSshServerIfRequired {
    $openSshServerInstalled = Get-WindowsCapability -Online `
        | Where-Object { $_.Name -match "OpenSSH\.Server*" } `
        | ForEach-Object { $_.State -eq "Installed" }
    if (-not($openSshServerInstalled)) {
        Write-Host "Installing OpenSSH server..."
        Add-WindowsCapability -Online -Name OpenSSH.Server~~~~0.0.1.0
        Start-Service sshd
        Set-Service -Name sshd -StartupType 'Automatic'
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
        Invoke-WebRequest 'https://github.com/kubernetes-sigs/sig-windows-tools/archive/00012ee6d171b105e7009bff8b2e42d96a45426f.zip' -Outfile .\sig-windows-tools.zip
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
            "Url" : "https://github.com/coreos/flannel/releases/download/v0.11.0/flanneld.exe"
            }
        ],
        "Plugin" : {
            "Name": "vxlan"
        },
        "InterfaceName" : "$Interface"
    },
    "Kubernetes" : {
        "Source" : {
            "Release" : "1.15.5",
            "Url" : "https://dl.k8s.io/v1.15.5/kubernetes-node-windows-amd64.tar.gz"
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

Write-Host "Installing WebDAV if required..."
InstallWebDAVIfRequired

Write-Host "Installing python if required..."
InstallPythonIfRequired -PythonDir $pythonDir

Write-Host "Installing chrome if required..."
InstallChromeIfRequired

Write-Host "Downloading scramble script if required..."
DownloadScrambleScriptIfRequired

Write-Host "Opening host ports..."
OpenPortIfRequired -Port 4000
OpenPortIfRequired -Port 8888

Write-Host "Installing OpenSSH server if required..."
InstallOpenSshServerIfRequired

Write-Host "Generating SSH keys..."
GenerateSshKeys -Path $homeDir

Write-Host "Downloading Sig Windows Tools if required..."
DownloadSigWindowsToolsIfRequired

Write-Host "Generating Sig Windows Tools dummy config file..."
InitSigWindowsToolsConfigFile -KubeHost "default" -KubeToken "default" -KubeCertHash "default" -KubeDir "$kubeDir" -Interface "Ethernet 3"

Write-Host "Installing kubernetes using Sig Windows Tools if required..."
InstallKubeUsingSigWindowsToolsIfRequired -KubeDir "$kubeDir"

Write-Host "Removing SSH keys..."
Remove-Item -Recurse -Force "$homeDir\.ssh"

Write-Host "Scheduling instance initialization on next launch..."
C:\ProgramData\Amazon\EC2-Windows\Launch\Scripts\InitializeInstance.ps1 -Schedule

Write-Host "Labelling instance as done..."
New-EC2Tag -Resource "$instanceId" -Tag @{Key="userdata"; Value="done"}

Stop-Transcript
