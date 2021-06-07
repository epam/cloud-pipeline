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

function StartOpenSSHServices {
    Set-Service -Name sshd -StartupType Automatic
    Start-Service sshd
}

function StartWebDAVServices {
    Set-Service WebClient -StartupType Automatic
    Set-Service MRxDAV -StartupType Automatic
    Start-Service WebClient
    Start-Service MRxDAV
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
    if (-not(Test-Path "$PythonDir")) {
        Write-Host "Installing python..."
        Invoke-WebRequest -Uri "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/python/3/python-3.8.9-amd64.exe" -OutFile "$workingDir\python-3.8.9-amd64.exe"
        & "$workingDir\python-3.8.9-amd64.exe" /quiet TargetDir=$PythonDir InstallAllUsers=1 PrependPath=1
        WaitForProcess -ProcessName "python-3.8.9-amd64"
    }
}

function InstallChromeIfRequired {
    if (-not(Test-Path "C:\Program Files\Google\Chrome\Application\chrome.exe")) {
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

function AddPublicKeyToAuthorizedKeys($SourcePath, $DestinationPath) {
    NewDirIfRequired -Path (Split-Path -Path $DestinationPath)
    Get-Content $SourcePath | Out-File -FilePath $DestinationPath -Encoding ascii -Force
    RestrictRegularUsersAccess -Path $DestinationPath
}

function CopyPrivateKey($SourcePath, $DestinationPath) {
    Copy-Item -Path (Split-Path -Path $SourcePath) -Destination (Split-Path -Path $DestinationPath) -Recurse
    RestrictRegularUsersAccess -Path $DestinationPath
}

function RestrictRegularUsersAccess($Path) {
    $acl = Get-Acl $Path
    $rules = $acl.Access `
        | Where-Object { $_.IdentityReference -in "NT AUTHORITY\SYSTEM","BUILTIN\Administrators" }
    $acl.SetAccessRuleProtection($true, $false)
    $rules | ForEach-Object { $acl.AddAccessRule($_) }
    $acl | Set-Acl $Path
}

function ConfigureAndRestartDockerDaemon {
    $dockerdaemonconfigfile = @"
{
   "insecure-registries" : ["cp-docker-registry.default.svc.cluster.local:31443"],
   "allow-nondistributable-artifacts": ["cp-docker-registry.default.svc.cluster.local:31443"]
}
"@
    $dockerdaemonconfigfile|Out-File -FilePath C:\ProgramData\docker\config\daemon.json -Encoding ascii -Force
    Restart-Service docker -Force
}

function DownloadSigWindowsToolsIfRequired {
    if (-not(Test-Path .\sig-windows-tools)) {
        NewDirIfRequired -Path .\sig-windows-tools
        Invoke-WebRequest "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/kube/1.15.4/win/sig-windows-tools-00012ee6d171b105e7009bff8b2e42d96a45426f.zip" -Outfile .\sig-windows-tools.zip
        tar -xvf .\sig-windows-tools.zip --strip-components=1 -C sig-windows-tools
    }
}

function PatchSigWindowsTools($KubeHost, $KubePort, $Dns) {
    $instanceId=$(Invoke-RestMethod -uri http://169.254.169.254/latest/meta-data/instance-id)
    $kubeClusterHelperContent = Get-Content .\sig-windows-tools\kubeadm\KubeClusterHelper.psm1
    $kubeClusterHelperContent[262] = '    Write-Host "Skipping node joining verification..."'
    $kubeClusterHelperContent[263] = '    return 1'
    $kubeClusterHelperContent[344] = '        $nodeName = "' + $instanceId + '"'
    $kubeClusterHelperContent[656] = '        "--hostname-override=' + $instanceId + '"'
    $kubeClusterHelperContent[704] = '        "--hostname-override=' + $instanceId + '"'
    $kubeClusterHelperContent[723] = '        hostnameOverride = "' + $instanceId + '";'
    $kubeClusterHelperContent[778] = '        -BinaryPathName "$kubeletBinPath --windows-service --v=6 --log-dir=$logDir --cert-dir=$env:SYSTEMDRIVE\var\lib\kubelet\pki --cni-bin-dir=$CniDir --cni-conf-dir=$CniConf --bootstrap-kubeconfig=/etc/kubernetes/bootstrap-kubelet.conf --kubeconfig=/etc/kubernetes/kubelet.conf --hostname-override=' + $instanceId + ' --pod-infra-container-image=$Global:PauseImage --enable-debugging-handlers  --cgroups-per-qos=false --enforce-node-allocatable=`"`" --logtostderr=false --network-plugin=cni --resolv-conf=`"`" --cluster-dns=`"$KubeDnsServiceIp`" --cluster-domain=cluster.local --feature-gates=$KubeletFeatureGates"'
    $kubeClusterHelperContent[782] = '    & cmd /c kubeadm join "$(GetAPIServerEndpoint)" --token "$Global:Token" --discovery-token-ca-cert-hash "$Global:CAHash" --ignore-preflight-errors "all" --node-name "' + $instanceId + '" ''2>&1'''
    $kubeClusterHelperContent[1212] = '    Write-Host "Returning kubernetes dns ip..."'
    $kubeClusterHelperContent[1213] = "    return '$Dns'"
    $kubeClusterHelperContent[1217] = '    Write-Host "Returning kubernetes master address..."'
    $kubeClusterHelperContent[1218] = "    return '$KubeHost`:$KubePort'"
    $kubeClusterHelperContent[1223] = '    Write-Host "Skipping nodes listing..."'
    $kubeClusterHelperContent[1228] = '    Write-Host "Skipping node deletion..."'
    $kubeClusterHelperContent | Set-Content .\sig-windows-tools\kubeadm\KubeClusterHelper.psm1
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
            "Release" : "1.15.5",
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
    RestrictRegularUsersAccess -Path .\Kubeclustervxlan.json
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

function WriteKubeConfig($KubeHost, $KubePort, $KubeNodeToken, $KubeDir) {
    $kubeConfig = @"
apiVersion: v1
kind: Config
preferences: {}

clusters:
- cluster:
    insecure-skip-tls-verify: true
    server: https://$KubeHost`:$KubePort
  name: kubernetes

contexts:
- context:
    cluster: kubernetes
    user: kubernetes-user
  name: kubernetes-user@kubernetes

users:
- name: kubernetes-user
  user:
    token: $KubeNodeToken

current-context: kubernetes-user@kubernetes
"@
    $kubeConfig | Out-File -FilePath "$KubeDir\config" -Encoding ascii -Force
    RestrictRegularUsersAccess -Path "$KubeDir\config"
}

function JoinKubeClusterUsingSigWindowsTools {
    .\sig-windows-tools\kubeadm\KubeCluster.ps1 -ConfigFile .\Kubeclustervxlan.json -join
}

function WaitAndConfigureDnsIfRequired($Dns, $Interface) {
    if (-not([string]::IsNullOrEmpty($Dns))) {
        while ($true) {
            try {
                Resolve-DnsName kubernetes.default.svc.cluster.local -Server $Dns -QuickTimeout -ErrorAction Stop
                break
            } catch {
                Write-Host "Still waiting for dns at $Dns..."
            }
        }
        Write-Host "Adding dns to network interface..."
        $interfaceIndex = Get-NetAdapter "$Interface" | ForEach-Object { $_.ifIndex }
        Set-DnsClientServerAddress -InterfaceIndex $interfaceIndex -ServerAddresses ("$Dns")
    }
}

function ConfigureAwsRoutes($Addrs, $Interface) {
    # See C:\ProgramData\Amazon\EC2-Windows\Launch\Module\Scripts\Add-Routes.ps1
    $interfaceIndex = Get-NetAdapter $Interface | ForEach-Object { $_.ifIndex }
    $networkAdapterConfig = Get-CimInstance -ClassName Win32_NetworkAdapterConfiguration -Filter "InterfaceIndex='$interfaceIndex'" `
        | Select-Object IPConnectionMetric, DefaultIPGateway
    foreach ($addr in $Addrs) {
        Remove-NetRoute -DestinationPrefix $addr -PolicyStore ActiveStore -Confirm:$false -ErrorAction SilentlyContinue
        Remove-NetRoute -DestinationPrefix $addr -PolicyStore PersistentStore -Confirm:$false -ErrorAction SilentlyContinue
        New-NetRoute -DestinationPrefix $addr -InterfaceIndex $interfaceIndex `
            -NextHop ([string] $networkAdapterConfig.DefaultIPGateway) -RouteMetric $networkAdapterConfig.IPConnectionMetric -ErrorAction Stop
    }
    w32tm /resync /rediscover /nowait
}

function ListenForConnection($Port) {
    $endpoint = New-Object System.Net.IPEndPoint ([System.Net.IPAddress]::Any, $Port)
    $listener = New-Object System.Net.Sockets.TcpListener $endpoint
    $listener.Start()
    $client = $listener.AcceptTcpClient()
    $stream = $client.GetStream();
    $reader = New-Object System.IO.StreamReader $stream
    do {
        $line = $reader.ReadLine()
        Write-Host $line -fore cyan
    } while ($line -and $line -ne ([char]4))
    $reader.Dispose()
    $stream.Dispose()
    $client.Dispose()
    $listener.Stop()
}

$kubeAddress = "@KUBE_IP@"
$kubeHost, $kubePort = $kubeAddress.split(":",2)
$kubeToken = "@KUBE_TOKEN@"
$kubeCertHash = "@KUBE_CERT_HASH@"
$kubeNodeToken = "@KUBE_NODE_TOKEN@"
$dnsProxyPost = "@dns_proxy_post@"

$interface = Get-NetAdapter | Where-Object { $_.Name -match "Ethernet \d+" } | ForEach-Object { $_.Name }
$interfacePost = "vEthernet ($interface)"
$awsAddrs = @("169.254.169.254/32", "169.254.169.250/32", "169.254.169.251/32", "169.254.169.249/32", "169.254.169.123/32", "169.254.169.253/32")

$homeDir = "$env:USERPROFILE"
$workingDir = "c:\init"
$hostDir = "c:\host"
$runsDir = "c:\runs"
$kubeDir = "c:\ProgramData\Kubernetes"
$pythonDir = "c:\python"
$initLog = "$workingDir\log.txt"

Write-Host "Creating system directories..."
NewDirIfRequired -Path $workingDir
NewDirIfRequired -Path $hostDir
NewDirIfRequired -Path $runsDir
NewDirIfRequired -Path $kubeDir

Write-Host "Starting logs capturing..."
Start-Transcript -path $initLog -append

Write-Host "Changing working directory..."
Set-Location -Path "$workingDir"

$restartRequired = $false

Write-Host "Installing nomachine if required..."
$restartRequired = (InstallNoMachineIfRequired | Select-Object -Last 1) -or $restartRequired
Write-Host "Restart required: $restartRequired"

Write-Host "Installing OpenSSH server if required..."
$restartRequired = (InstallOpenSshServerIfRequired | Select-Object -Last 1) -or $restartRequired
Write-Host "Restart required: $restartRequired"

Write-Host "Installing WebDAV if required..."
$restartRequired = (InstallWebDAVIfRequired | Select-Object -Last 1) -or $restartRequired
Write-Host "Restart required: $restartRequired"

Write-Host "Installing pGina if required..."
$restartRequired = (InstallPGinaIfRequired | Select-Object -Last 1) -or $restartRequired
Write-Host "Restart required: $restartRequired"

Write-Host "Restarting computer if required..."
if ($restartRequired) {
    Write-Host "Restarting computer..."
    Stop-Transcript
    Restart-Computer -Force
    Exit
}

Write-Host "Starting OpenSSH services..."
StartOpenSSHServices

Write-Host "Starting WebDAV services..."
StartWebDAVServices

Write-Host "Installing python if required..."
InstallPythonIfRequired -PythonDir $pythonDir

Write-Host "Installing chrome if required..."
InstallChromeIfRequired

Write-Host "Opening host ports..."
OpenPortIfRequired -Port 4000
OpenPortIfRequired -Port 8888

Write-Host "Generating SSH keys..."
GenerateSshKeys -Path $homeDir

Write-Host "Adding node SSH keys to authorized keys..."
AddPublicKeyToAuthorizedKeys -SourcePath "$homeDir\.ssh\id_rsa.pub" -DestinationPath C:\Windows\.ssh\authorized_keys
AddPublicKeyToAuthorizedKeys -SourcePath "$homeDir\.ssh\id_rsa.pub" -DestinationPath C:\ProgramData\ssh\administrators_authorized_keys

Write-Host "Publishing node SSH keys..."
CopyPrivateKey -SourcePath "$homeDir\.ssh\id_rsa" -DestinationPath "$hostDir\.ssh\id_rsa"

Write-Host "Configuring docker daemon..."
ConfigureAndRestartDockerDaemon

Write-Host "Downloading Sig Windows Tools if required..."
DownloadSigWindowsToolsIfRequired

Write-Host "Patching KubeClusterHelper.psm1 script to ignore all preflight errors..."
PatchSigWindowsTools -KubeHost $kubeHost -KubePort $kubePort -Dns $dnsProxyPost

Write-Host "Generating Sig Windows Tools config file..."
InitSigWindowsToolsConfigFile -KubeHost $kubeHost -KubeToken $kubeToken -KubeCertHash $kubeCertHash -KubeDir $kubeDir -Interface $interface

Write-Host "Installing kubernetes using Sig Windows Tools if required..."
InstallKubeUsingSigWindowsToolsIfRequired -KubeDir $kubeDir

Write-Host "Writing kubernetes config..."
WriteKubeConfig -KubeHost $kubeHost -KubePort $kubePort -KubeNodeToken $kubeNodeToken -KubeDir $kubeDir

Write-Host "Joining kubernetes cluster using Sig Windows Tools..."
JoinKubeClusterUsingSigWindowsTools

Write-Host "Waiting for dns to be accessible if required..."
WaitAndConfigureDnsIfRequired -Dns $dnsProxyPost -Interface $interfacePost

Write-Host "Configuring AWS routes..."
ConfigureAwsRoutes -Addrs $awsAddrs -Interface $interfacePost

Write-Host "Listening on port 8888..."
ListenForConnection -Port 8888

Stop-Transcript
