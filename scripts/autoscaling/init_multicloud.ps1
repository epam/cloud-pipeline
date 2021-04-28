$ApiUser="@API_USER@"
$ApiToken="@API_TOKEN@"
$KubeIp="@KUBE_IP@"
$HostIp=$KubeIp.split(":",2)[0]
$KubeToken="@KUBE_TOKEN@"
$KubeCertHash="@KUBE_CERT_HASH@"
$kubeConfigFile = @"
@KUBE_CONFIG@
"@
$dnsProxyPost="@dns_proxy_post@"

$userName="$env:username"
$homeDir="$env:USERPROFILE"
$workingDir="c:\init"
$hostDir="c:\host"
$runsDir="c:\runs"
$computerName=$(hostname)
$instanceId=$(Invoke-RestMethod -uri http://169.254.169.254/latest/meta-data/instance-id)

Write-Host "User: $userName"
Write-Host "User Home: $homeDir"
Write-Host "Working Directory: $workingDir"
Write-Host "Host Directory: $hostDir"
Write-Host "Runs Directory: $runsDir"
Write-Host "Computer Name: $computerName"
Write-Host "Instance Id: $instanceId"

Write-Host "Creating system directories..."
if (-not(Test-Path $workingDir)) {
    New-Item -Path "$workingDir" -ItemType "Directory" -Force
}
if (-not(Test-Path "$hostDir")) {
    New-Item -Path "$hostDir" -ItemType "Directory" -Force
}
if (-not(Test-Path "$runsDir")) {
    New-Item -Path "$runsDir" -ItemType "Directory" -Force
}

Write-Host "Changing working directory..."
Set-Location -Path "$workingDir"

$restartRequired=$false

$nomachineInstalled = Get-Service -Name nxservice `
    | Measure-Object `
    | ForEach-Object { $_.Count -gt 0 }
if (-not($nomachineInstalled)) {
    Write-Host "Installing nomachine..."
    Invoke-WebRequest 'https://download.nomachine.com/download/7.4/Windows/nomachine_7.4.1_1.exe' -Outfile .\nomachine.exe
    cmd /c "nomachine.exe /verysilent"
    c:\ProgramData\NoMachine\nxserver\nxserver --startmode manual
    $restartRequired=$true
}

if ($instanceId -ne $computerName) {
    Write-Host "Renaming computer from $computerName to $instanceId..."
    Rename-Computer -NewName $instanceId -Force
    $restartRequired=$true
}

if ($restartRequired) {
    Write-Host "Restarting computer..."
    Restart-Computer -Force
}

Write-Host "Configuring farewall..."
New-NetFirewallRule -DisplayName "Cloud Pipeline NoMachine Inbound 4000 Port" `
                    -Direction Inbound `
                    -LocalPort 4000 `
                    -Protocol TCP `
                    -Action Allow

New-NetFirewallRule -DisplayName "Cloud Pipeline Node Readiness Inbound 8888 Port" `
                    -Direction Inbound `
                    -LocalPort 8888 `
                    -Protocol TCP `
                    -Action Allow

$openSshServerInstalled = Get-WindowsCapability -Online `
    | Where-Object { $_.Name -match "OpenSSH\.Server*" } `
    | ForEach-Object { $_.State -eq "Installed" }
if (-not($openSshServerInstalled)) {
    Write-Host "Installing OpenSSH server..."
    Add-WindowsCapability -Online -Name OpenSSH.Server~~~~0.0.1.0
    Start-Service sshd
    Set-Service -Name sshd -StartupType 'Automatic'
}

Write-Host "Generating ssh keys..."
if (!(Test-Path "$homeDir\.ssh")) {
    New-Item -Path "$homeDir\.ssh" -ItemType "Directory" -Force
}
cmd /c "ssh-keygen.exe -t rsa -N """" -f ""$homeDir\.ssh\id_rsa"""

Write-Host "Adding generated ssh keys to administrators authorized hosts..."
Get-Content "$homeDir\.ssh\id_rsa.pub" `
    | Out-File -FilePath C:\ProgramData\ssh\administrators_authorized_keys -Encoding ascii -Force
$acl = Get-Acl C:\ProgramData\ssh\administrators_authorized_keys
$rules = $acl.Access `
    | Where-Object { $_.IdentityReference -in "NT AUTHORITY\SYSTEM","BUILTIN\Administrators" }
$acl.SetAccessRuleProtection($true, $false)
$rules | ForEach-Object { $acl.AddAccessRule($_) }
$acl | Set-Acl C:\ProgramData\ssh\administrators_authorized_keys
Copy-Item -Path "$homeDir\.ssh" -Destination "$hostDir\.ssh" -Recurse
$acl = Get-Acl "$hostDir\.ssh\id_rsa"
$rules = $acl.Access `
    | Where-Object { $_.IdentityReference -in "NT AUTHORITY\SYSTEM","BUILTIN\Administrators" }
$acl.SetAccessRuleProtection($true, $false)
$rules | ForEach-Object { $acl.AddAccessRule($_) }
$acl | Set-Acl "$hostDir\.ssh\id_rsa"

Write-Host "Configuring docker daemon to use default docker registy..."
$dockerdaemonconfigfile = @"
{
   "insecure-registries" : ["cp-docker-registry.default.svc.cluster.local:31443"],
   "allow-nondistributable-artifacts": ["cp-docker-registry.default.svc.cluster.local:31443"]
}
"@
$dockerdaemonconfigfile|Out-File -FilePath C:\ProgramData\docker\config\daemon.json -Encoding ascii -Force
Restart-Service docker -Force

Write-Host "Downloading scripts for cluster joining..."
Invoke-WebRequest 'https://github.com/kubernetes-sigs/sig-windows-tools/archive/00012ee6d171b105e7009bff8b2e42d96a45426f.zip' -Outfile .\sig-windows-tools.zip
if (!(Test-Path .\sig-windows-tools)) { New-Item -Path .\sig-windows-tools -ItemType "Directory" -Force }
tar -xvf .\sig-windows-tools.zip --strip-components=1 -C sig-windows-tools

Write-Host "Patching KubeClusterHelper.psm1 script to ignore all preflight errors..."
$kubeClusterHelperContent = Get-Content .\sig-windows-tools\kubeadm\KubeClusterHelper.psm1
$kubeClusterHelperContent[783] = '& cmd /c kubeadm join "$(GetAPIServerEndpoint)" --token "$Global:Token" --discovery-token-ca-cert-hash "$Global:CAHash" --ignore-preflight-errors "all" ''2>&1'''
$kubeClusterHelperContent | Set-Content .\sig-windows-tools\kubeadm\KubeClusterHelper.psm1

Write-Host "Writing kubernetes node joining config..."
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
        "InterfaceName" : "Ethernet 3"
    },
    "Kubernetes" : {
        "Source" : {
            "Release" : "1.15.5",
            "Url" : "https://dl.k8s.io/v1.15.5/kubernetes-node-windows-amd64.tar.gz"
        },
        "ControlPlane" : {
            "IpAddress" : "$HostIp",
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
        "Destination" : "C:\\ProgramData\\Kubernetes"
    }
}
"@
$configfile|Out-File -FilePath .\Kubeclustervxlan.json -Encoding ascii -Force

Write-Host "Writing kubernetes config..."
if (!(Test-Path "C:\ProgramData\Kubernetes")) { New-Item -Path "C:\ProgramData\Kubernetes" -ItemType "Directory" -Force }
$kubeConfigFile|Out-File -FilePath C:\ProgramData\Kubernetes\config -Encoding ascii -Force

Write-Host "Preparing node for joining cluster..."
.\sig-windows-tools\kubeadm\KubeCluster.ps1 -ConfigFile .\Kubeclustervxlan.json -install

Write-Host "Joining cluster..."
.\sig-windows-tools\kubeadm\KubeCluster.ps1 -ConfigFile .\Kubeclustervxlan.json -join

if (-not([string]::IsNullOrEmpty($dnsProxyPost))) {
    Write-Host "Waiting for dns to be accessible..."
    while ($true) {
        try {
            Resolve-DnsName kubernetes.default.svc.cluster.local -Server $dnsProxyPost -QuickTimeout
            break
        } catch {
            Write-Error "$_"
        }
    }
    Write-Host "Adding dns to network interface..."
    $interfaceIndex = Get-NetAdapter "vEthernet (Ethernet 3)" | ForEach-Object { $_.ifIndex }
    Set-DnsClientServerAddress -InterfaceIndex $interfaceIndex -ServerAddresses ("$dnsProxyPost")
}

Write-Host "Listening on port 8888..."
$endpoint = New-Object System.Net.IPEndPoint ([System.Net.IPAddress]::Any, 8888)
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
