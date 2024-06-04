function OpenPortIfRequired($Port) {
    $FilewallRuleName = "Cloud Pipeline Inbound $Port Port"
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

function InitializeDisks {
    Get-Disk `
        | Where-Object { $_.PartitionStyle -eq "raw" } `
        | Initialize-Disk -PartitionStyle MBR -PassThru `
        | New-Partition -AssignDriveLetter -UseMaximumSize `
        | Format-Volume -FileSystem NTFS -Confirm:$false `
        | ForEach-Object { $_.DriveLetter + ":" } `
        | ForEach-Object { AllowRegularUsersAccess -Path $_ }
}

function InstallNoMachineIfRequired($GlobalDistributionUrl) {
    $restartRequired = $false
    $nomachineInstalled = Get-Service -Name nxservice `
    | Measure-Object `
    | ForEach-Object { $_.Count -gt 0 }
    if (-not($nomachineInstalled)) {
        Write-Host "Installing NoMachine..."
        Invoke-WebRequest "${GlobalDistributionUrl}tools/nomachine/nomachine_7.6.2_4.exe" -Outfile .\nomachine.exe
        cmd /c "nomachine.exe /verysilent"
        @"

VirtualDesktopAuthorization 0
PhysicalDesktopAuthorization 0
AutomaticDisconnection 0
ConnectionsLimit 1
ConnectionsUserLimit 1
"@ | Out-File -FilePath "C:\Program Files (x86)\NoMachine\etc\server.cfg" -Encoding ascii -Force -Append
        $restartRequired=$true
    }
    return $restartRequired
}

function InstallOpenSshServerIfRequired {
    $restartRequired = $false
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
    $restartRequired = $false
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

function InstallPGinaIfRequired($GlobalDistributionUrl) {
    $restartRequired = $false
    $pGinaInstalled = Get-Service -Name pgina `
        | Measure-Object `
        | ForEach-Object { $_.Count -gt 0 }
    if (-not($pGinaInstalled)) {
        Write-Host "Installing pGina..."
        Invoke-WebRequest "${GlobalDistributionUrl}tools/pgina/pGina-3.2.4.0-setup.exe" -OutFile "pGina-3.2.4.0-setup.exe"
        Invoke-WebRequest "${GlobalDistributionUrl}tools/pgina/vcredist_x64.exe" -OutFile "vcredist_x64.exe"
        .\pGina-3.2.4.0-setup.exe /S /D=C:\Program Files\pGina
        WaitForProcess -ProcessName "pGina-3.2.4.0-setup"
        .\vcredist_x64.exe /quiet
        WaitForProcess -ProcessName "vcredist_x64"
        Invoke-WebRequest "${GlobalDistributionUrl}tools/pgina/pGina.Plugin.AuthenticateAllPlugin.dll" -OutFile "C:\Program Files\pGina\Plugins\Contrib\pGina.Plugin.AuthenticateAllPlugin.dll"
        Set-ItemProperty -Path "HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Authentication\Credential Providers\{d0befefb-3d2c-44da-bbad-3b2d04557246}" -Name "Disabled" -Type "DWord" -Value "1"
        Set-ItemProperty -Path "HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Authentication\Credential Providers\{d0befefb-3d2c-44da-bbad-3b2d04557246}" -Name "Disabled" -Type "DWord" -Value "1"
        $restartRequired=$true
    }
    return $restartRequired
}

function InstallDockerIfRequired {
    $restartRequired = $false
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

function InstallPythonIfRequired($PythonDir, $GlobalDistributionUrl) {
    if (-not(Test-Path "$PythonDir")) {
        Write-Host "Installing python..."
        Invoke-WebRequest -Uri "${GlobalDistributionUrl}tools/python/3/python-3.8.9-amd64.exe" -OutFile "$workingDir\python-3.8.9-amd64.exe"
        & "$workingDir\python-3.8.9-amd64.exe" /quiet TargetDir=$PythonDir InstallAllUsers=1 PrependPath=1
        WaitForProcess -ProcessName "python-3.8.9-amd64"
    }
}

function InstallChromeIfRequired($GlobalDistributionUrl) {
    if (-not(Test-Path "C:\Program Files\Google\Chrome\Application\chrome.exe")) {
        Write-Host "Installing chrome..."
        Invoke-WebRequest "${GlobalDistributionUrl}tools/chrome/ChromeSetup.exe" -Outfile "$workingDir\ChromeSetup.exe"
        & $workingDir\ChromeSetup.exe /silent /install
        WaitForProcess -ProcessName "ChromeSetup"
    }
}

function InstallDokanyIfRequired($DokanyDir, $GlobalDistributionUrl) {
    if (-not (Test-Path "$DokanyDir")) {
        Write-Host "Installing Dokany..."
        Invoke-WebRequest "${GlobalDistributionUrl}tools/dokany/DokanSetup.exe" -OutFile "$workingDir\DokanSetup.exe"
        & "$workingDir\DokanSetup.exe" /quiet /silent /verysilent
        WaitForProcess -ProcessName "DokanSetup"
    }
}

function InstallNiceDcvIfRequired {
    $niceDcvInstalled = Get-Service -Name "DCV Server" `
        | Measure-Object `
        | ForEach-Object { $_.Count -gt 0 }
    if (-not ($niceDcvInstalled)) {
        Invoke-WebRequest "https://d1uj6qtbmh3dt5.cloudfront.net/2021.2/Servers/nice-dcv-server-x64-Release-2021.2-11048.msi" -outfile "$workingDir\nice-dcv-server-x64-Release-2021.2-11048.msi"
        Start-Process -FilePath "$workingDir\nice-dcv-server-x64-Release-2021.2-11048.msi" -ArgumentList "ADDLOCAL=ALL /quiet /norestart /l*v $workingDir\nice_dcv_install.log" -Wait -PassThru
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

function AllowRegularUsersAccess($Path) {
    $acl = Get-Acl $Path
    $rule = New-Object System.Security.AccessControl.FileSystemAccessRule("BUILTIN\Users", "Write", "ContainerInherit,ObjectInherit", "None", "Allow")
    $acl.AddAccessRule($rule)
    $acl | Set-Acl $Path
}

function RestrictRegularUsersAccess($Path) {
    $acl = Get-Acl $Path
    $rules = $acl.Access `
        | Where-Object { $_.IdentityReference -in "NT AUTHORITY\SYSTEM","BUILTIN\Administrators" }
    $acl.SetAccessRuleProtection($true, $false)
    $rules | ForEach-Object { $acl.AddAccessRule($_) }
    $acl | Set-Acl $Path
}

function ConfigureContainerd {
    Stop-Service containerd

    $containerdconfigfile = @"
disabled_plugins = []
imports = []
oom_score = 0
plugin_dir = ""
required_plugins = []
root = "C:\\ProgramData\\containerd\\root"
state = "C:\\ProgramData\\containerd\\state"
temp = ""
version = 2

[cgroup]
  path = ""

[debug]
  address = ""
  format = ""
  gid = 0
  level = ""
  uid = 0

[grpc]
  address = "\\\\.\\pipe\\containerd-containerd"
  gid = 0
  max_recv_message_size = 16777216
  max_send_message_size = 16777216
  tcp_address = ""
  tcp_tls_ca = ""
  tcp_tls_cert = ""
  tcp_tls_key = ""
  uid = 0

[metrics]
  address = ""
  grpc_histogram = false

[proxy_plugins]

[stream_processors]

  [stream_processors."io.containerd.ocicrypt.decoder.v1.tar"]
    accepts = ["application/vnd.oci.image.layer.v1.tar+encrypted"]
    args = ["--decryption-keys-path", "C:\\Program Files\\containerd\\ocicrypt\\keys"]
    env = ["OCICRYPT_KEYPROVIDER_CONFIG=C:\\Program Files\\containerd\\ocicrypt\\ocicrypt_keyprovider.conf"]
    path = "ctd-decoder"
    returns = "application/vnd.oci.image.layer.v1.tar"

  [stream_processors."io.containerd.ocicrypt.decoder.v1.tar.gzip"]
    accepts = ["application/vnd.oci.image.layer.v1.tar+gzip+encrypted"]
    args = ["--decryption-keys-path", "C:\\Program Files\\containerd\\ocicrypt\\keys"]
    env = ["OCICRYPT_KEYPROVIDER_CONFIG=C:\\Program Files\\containerd\\ocicrypt\\ocicrypt_keyprovider.conf"]
    path = "ctd-decoder"
    returns = "application/vnd.oci.image.layer.v1.tar+gzip"

[timeouts]
  "io.containerd.timeout.bolt.open" = "0s"
  "io.containerd.timeout.metrics.shimstats" = "2s"
  "io.containerd.timeout.shim.cleanup" = "5s"
  "io.containerd.timeout.shim.load" = "5s"
  "io.containerd.timeout.shim.shutdown" = "3s"
  "io.containerd.timeout.task.state" = "2s"

[ttrpc]
  address = ""
  gid = 0
  uid = 0

[plugins]

  [plugins."io.containerd.gc.v1.scheduler"]
    deletion_threshold = 0
    mutation_threshold = 100
    pause_threshold = 0.02
    schedule_delay = "0s"
    startup_delay = "100ms"

  [plugins."io.containerd.grpc.v1.cri"]
    cdi_spec_dirs = []
    device_ownership_from_security_context = false
    disable_apparmor = false
    disable_cgroup = false
    disable_hugetlb_controller = false
    disable_proc_mount = false
    disable_tcp_service = true
    drain_exec_sync_io_timeout = "0s"
    enable_cdi = false
    enable_selinux = false
    enable_tls_streaming = false
    enable_unprivileged_icmp = false
    enable_unprivileged_ports = false
    ignore_image_defined_volumes = false
    image_pull_progress_timeout = "5m0s"
    max_concurrent_downloads = 3
    max_container_log_line_size = 16384
    netns_mounts_under_state_dir = false
    restrict_oom_score_adj = false
    sandbox_image = "amazonaws.com/eks/pause-windows:latest"
    selinux_category_range = 0
    stats_collect_period = 10
    stream_idle_timeout = "4h0m0s"
    stream_server_address = "127.0.0.1"
    stream_server_port = "0"
    systemd_cgroup = false
    tolerate_missing_hugetlb_controller = false
    unset_seccomp_profile = ""

    [plugins."io.containerd.grpc.v1.cri".cni]
      bin_dir = "C:\\Program Files\\Amazon\\EKS\\cni"
      conf_dir = "C:\\ProgramData\\Amazon\\EKS\\cni\\config"
      conf_template = ""
      ip_pref = ""
      max_conf_num = 1
      setup_serially = false

    [plugins."io.containerd.grpc.v1.cri".containerd]
      default_runtime_name = "runhcs-wcow-process"
      disable_snapshot_annotations = false
      discard_unpacked_layers = false
      ignore_blockio_not_enabled_errors = false
      ignore_rdt_not_enabled_errors = false
      no_pivot = false
      snapshotter = "windows"

      [plugins."io.containerd.grpc.v1.cri".containerd.default_runtime]
        base_runtime_spec = ""
        cni_conf_dir = ""
        cni_max_conf_num = 0
        container_annotations = []
        pod_annotations = []
        privileged_without_host_devices = false
        privileged_without_host_devices_all_devices_allowed = false
        runtime_engine = ""
        runtime_path = ""
        runtime_root = ""
        runtime_type = ""
        sandbox_mode = ""
        snapshotter = ""

        [plugins."io.containerd.grpc.v1.cri".containerd.default_runtime.options]

      [plugins."io.containerd.grpc.v1.cri".containerd.runtimes]

        [plugins."io.containerd.grpc.v1.cri".containerd.runtimes.runhcs-wcow-hypervisor]
          base_runtime_spec = ""
          cni_conf_dir = ""
          cni_max_conf_num = 0
          container_annotations = ["io.microsoft.container.*"]
          pod_annotations = ["io.microsoft.virtualmachine.*"]
          privileged_without_host_devices = false
          privileged_without_host_devices_all_devices_allowed = false
          runtime_engine = ""
          runtime_path = ""
          runtime_root = ""
          runtime_type = "io.containerd.runhcs.v1"
          sandbox_mode = ""
          snapshotter = ""

          [plugins."io.containerd.grpc.v1.cri".containerd.runtimes.runhcs-wcow-hypervisor.options]
            SandboxIsolation = 1
            ScaleCpuLimitsToSandbox = true

        [plugins."io.containerd.grpc.v1.cri".containerd.runtimes.runhcs-wcow-process]
          base_runtime_spec = ""
          cni_conf_dir = ""
          cni_max_conf_num = 0
          container_annotations = ["io.microsoft.container.*"]
          pod_annotations = []
          privileged_without_host_devices = false
          privileged_without_host_devices_all_devices_allowed = false
          runtime_engine = ""
          runtime_path = ""
          runtime_root = ""
          runtime_type = "io.containerd.runhcs.v1"
          sandbox_mode = ""
          snapshotter = ""

          [plugins."io.containerd.grpc.v1.cri".containerd.runtimes.runhcs-wcow-process.options]

      [plugins."io.containerd.grpc.v1.cri".containerd.untrusted_workload_runtime]
        base_runtime_spec = ""
        cni_conf_dir = ""
        cni_max_conf_num = 0
        container_annotations = []
        pod_annotations = []
        privileged_without_host_devices = false
        privileged_without_host_devices_all_devices_allowed = false
        runtime_engine = ""
        runtime_path = ""
        runtime_root = ""
        runtime_type = ""
        sandbox_mode = ""
        snapshotter = ""

        [plugins."io.containerd.grpc.v1.cri".containerd.untrusted_workload_runtime.options]

  [plugins."io.containerd.internal.v1.opt"]
    path = "C:\\ProgramData\\containerd\\root\\opt"

  [plugins."io.containerd.internal.v1.restart"]
    interval = "10s"

  [plugins."io.containerd.internal.v1.tracing"]
    sampling_ratio = 1.0
    service_name = "containerd"

  [plugins."io.containerd.metadata.v1.bolt"]
    content_sharing_policy = "shared"

  [plugins."io.containerd.nri.v1.nri"]
    disable = true
    disable_connections = false
    plugin_config_path = "/etc/nri/conf.d"
    plugin_path = "/opt/nri/plugins"
    plugin_registration_timeout = "5s"
    plugin_request_timeout = "2s"
    socket_path = "/var/run/nri/nri.sock"

  [plugins."io.containerd.runtime.v2.task"]
    platforms = ["windows/amd64", "linux/amd64"]
    sched_core = false

  [plugins."io.containerd.service.v1.diff-service"]
    default = ["windows", "windows-lcow"]

  [plugins."io.containerd.service.v1.tasks-service"]
    blockio_config_file = ""
    rdt_config_file = ""

  [plugins."io.containerd.tracing.processor.v1.otlp"]
    endpoint = ""
    insecure = false
    protocol = ""

  [plugins."io.containerd.transfer.v1.local"]
    config_path = ""
    max_concurrent_downloads = 3
    max_concurrent_uploaded_layers = 3

    [[plugins."io.containerd.transfer.v1.local".unpack_config]]
      differ = ""
      platform = "windows/amd64"
      snapshotter = "windows"

  [plugins."io.containerd.grpc.v1.cri".registry]
    config_path = ""
    [plugins."io.containerd.grpc.v1.cri".registry.configs]
"@
    $containerdconfigfile|Out-File -FilePath 'C:\Program Files\containerd\config.toml' -Encoding ascii -Force

    $dockerRegistryUrls = "@DOCKER_REGISTRY_URLS@"
    $dockerRegistryUrls.Split(",") | ForEach {
         $containerdConfigFileRegistryConfig = @"
      [plugins."io.containerd.grpc.v1.cri".registry.configs."$_"]
        [plugins."io.containerd.grpc.v1.cri".registry.configs."$_".tls]
          insecure_skip_verify = true

"@
         $containerdConfigFileRegistryConfig| Add-Content -Path 'C:\Program Files\containerd\config.toml' -Encoding ascii -Force
    }
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

function ConfigureLoopbackRouteIfEnabled($Preferences, $Interface) {
    $loopbackRouteEnabled = $Preferences `
        | Where-Object { $_.Name -eq "cluster.windows.node.loopback.route" } `
        | ForEach-Object { $_.Value -eq "true" }
    if ($loopbackRouteEnabled) {
        Write-Host "Configuring loopback route..."
        $interfaceIndex = Get-NetAdapter $Interface | ForEach-Object { $_.ifIndex }
        $interfaceIp = Get-NetIPAddress -AddressFamily IPv4 -InterfaceIndex $interfaceIndex | Select-Object -ExpandProperty IPAddress
        $Addrs = @("$interfaceIp/32")
        ConfigureAwsRoutes -Addrs $Addrs -Interface $Interface
    }
}

function LoadPreferences($ApiUrl, $ApiToken) {
    return RequestApi -ApiUrl $ApiUrl -ApiToken $ApiToken -HttpMethod "GET" -ApiMethod "preferences"
}

function RequestApi($ApiUrl, $ApiToken, $HttpMethod, $ApiMethod, $Body = $null) {
    Write-Host "Requesting ${ApiUrl}${ApiMethod}..."
    $Response = Invoke-RestMethod -Method $HttpMethod `
                                  -Uri "${ApiUrl}${ApiMethod}" `
                                  -Body $Body `
                                  -Headers @{
                                      "Authorization" = "Bearer $ApiToken"
                                      "Content-Type" = "application/json"
                                  }
    if ($Response.status -ne "OK") {
        Write-Error $Response.message
        return $null
    }
    return $Response.payload
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

$apiUrl = "@API_URL@"
$apiToken = "@API_TOKEN@"
$kubeAddress = "@KUBE_IP@"
$kubeHost, $kubePort = $kubeAddress.split(":",2)
$kubeToken = "@KUBE_TOKEN@"
$kubeCertHash = "@KUBE_CERT_HASH@"
$kubeNodeToken = "@KUBE_NODE_TOKEN@"
$dnsProxyPost = "@dns_proxy_post@"
$globalDistributionUrl = "@GLOBAL_DISTRIBUTION_URL@"

$interface = Get-NetAdapter | Where-Object { $_.Name -match "^Ethernet \d+" } | ForEach-Object { $_.Name }
$interfacePost = "vEthernet ($interface)"
$awsAddrs = @("169.254.169.254/32", "169.254.169.250/32", "169.254.169.251/32", "169.254.169.249/32", "169.254.169.123/32", "169.254.169.253/32")

$homeDir = "$env:USERPROFILE"
$workingDir = "c:\init"
$hostDir = "c:\host"
$runsDir = "c:\runs"
$kubeDir = "c:\ProgramData\Kubernetes"
$pythonDir = "c:\python"
$dokanyDir = "C:\Program Files\Dokan\Dokan Library-1.5.0"
$initLog = "$workingDir\log.txt"


Write-Host "Starting logs capturing..."
Start-Transcript -path $initLog -append

Write-Host "Initializing disks..."
InitializeDisks

Write-Host "Starting OpenSSH services..."
StartOpenSSHServices

Write-Host "Starting WebDAV services..."
StartWebDAVServices

Write-Host "Installing python if required..."
InstallPythonIfRequired -PythonDir $pythonDir -GlobalDistributionUrl $globalDistributionUrl

Write-Host "Installing chrome if required..."
InstallChromeIfRequired -GlobalDistributionUrl $globalDistributionUrl

Write-Host "Installing Dokany if required..."
InstallDokanyIfRequired -DokanyDir $dokanyDir -GlobalDistributionUrl $globalDistributionUrl

Write-Host "Installing NICE DCV if required..."
InstallNiceDcvIfRequired


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

Write-Host "Configuring containerd daemon..."
ConfigureContainerd

& 'C:\Program Files\Amazon\EKS\Start-EKSBootstrap.ps1' -EKSClusterName "@KUBE_CLUSTER_NAME@" 3>&1 4>&1 5>&1 6>&1

Write-Host "Waiting for dns to be accessible if required..."
WaitAndConfigureDnsIfRequired -Dns $dnsProxyPost -Interface $interfacePost

Write-Host "Loading preferences..."
$preferences = LoadPreferences -ApiUrl $apiUrl -ApiToken $apiToken

Write-Host "Configuring loopback route if it is enabled in preferences..."
ConfigureLoopbackRouteIfEnabled -Preferences $preferences -Interface $interfacePost

Write-Host "Listening on port 8888..."
ListenForConnection -Port 8888

Stop-Transcript
