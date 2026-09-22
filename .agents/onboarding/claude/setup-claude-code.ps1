# Installs Claude Code if it is missing, then writes %USERPROFILE%\.claude\settings.json for this
# team's Amazon Bedrock inference profiles. Prompts for the four values it cannot know, never echoing
# the secret ones; an existing settings file is backed up before it is replaced.
# macOS, Linux and WSL have their own copy of this, setup-claude-code.sh.
# See README.md.

$ErrorActionPreference = 'Stop'

$settingsDir = Join-Path $env:USERPROFILE '.claude'
$settingsFile = Join-Path $settingsDir 'settings.json'

if (-not [Environment]::UserInteractive -or [Console]::IsInputRedirected) {
    Write-Error 'This script asks questions, so run it in an interactive PowerShell window.'
    exit 1
}

# --- Claude Code -------------------------------------------------------------------------------

$claude = Get-Command claude -ErrorAction SilentlyContinue
if ($claude) {
    Write-Host "Claude Code is already installed: $($claude.Source)"
} else {
    Write-Host 'Installing Claude Code...'
    Invoke-RestMethod https://claude.ai/install.ps1 | Invoke-Expression
    if (-not (Get-Command claude -ErrorAction SilentlyContinue)) {
        Write-Host 'Installed, but "claude" is not on PATH in this shell yet — open a new one.'
        Write-Host 'The settings file below is written either way.'
    }
}

# --- the values only you have ------------------------------------------------------------------

$existingEnv = @{}
if (Test-Path $settingsFile) {
    try {
        $parsed = Get-Content -Raw -Path $settingsFile | ConvertFrom-Json
        if ($parsed.env) {
            foreach ($entry in $parsed.env.PSObject.Properties) {
                if ($entry.Value -is [string]) { $existingEnv[$entry.Name] = $entry.Value }
            }
        }
    } catch {
        Write-Host "Could not read the settings you already have — every value is asked for afresh."
    }
}

function Get-Current {
    param([string]$Key)
    if ($existingEnv.ContainsKey($Key)) { return $existingEnv[$Key] }
    return ''
}

function Read-Required {
    param([string]$Prompt, [string]$Current = '', [switch]$Hidden)
    $shown = $Prompt
    if (-not [string]::IsNullOrWhiteSpace($Current)) {
        if ($Hidden) { $shown = "$Prompt [Enter keeps the current one]" } else { $shown = "$Prompt [$Current]" }
    }
    while ($true) {
        try {
            if ($Hidden) {
                $secure = Read-Host -Prompt $shown -AsSecureString
                $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
                try {
                    $value = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
                } finally {
                    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
                }
            } else {
                $value = Read-Host -Prompt $shown
            }
        } catch {
            Write-Host ''
            Write-Error 'Aborted — no settings were written.'
            exit 1
        }
        $value = $value.Trim()
        if ([string]::IsNullOrWhiteSpace($value)) { $value = $Current }
        if (-not [string]::IsNullOrWhiteSpace($value)) { return $value }
        Write-Host '  A value is required.'
    }
}

Write-Host ''
Write-Host 'Paste the values you were given. Ask your team lead if you do not have them.'
$sonnetProfile = Read-Required 'Sonnet 5 inference profile ID' (Get-Current 'ANTHROPIC_DEFAULT_SONNET_MODEL')
$opusProfile   = Read-Required 'Opus 5 inference profile ID'   (Get-Current 'ANTHROPIC_DEFAULT_OPUS_MODEL')
$accessKey     = Read-Required 'AWS access key ID'             (Get-Current 'AWS_ACCESS_KEY_ID')
$secretKey     = Read-Required 'AWS secret access key'         (Get-Current 'AWS_SECRET_ACCESS_KEY') -Hidden

$regionDefault = Get-Current 'AWS_REGION'
if ([string]::IsNullOrWhiteSpace($regionDefault)) { $regionDefault = 'us-east-1' }
$awsRegion     = Read-Required 'AWS region' $regionDefault

# --- write the settings ------------------------------------------------------------------------

New-Item -ItemType Directory -Force -Path $settingsDir | Out-Null

function Restrict-ToOwner {
    param([string]$Path)
    # Owner-only, as close as Windows ACLs get to chmod 600.
    & icacls $Path /inheritance:r /grant:r "$($env:USERNAME):(R,W)" > $null 2>&1
}

if (Test-Path $settingsFile) {
    $backup = "$settingsFile.bak.$(Get-Date -Format 'yyyyMMddHHmmss')"
    Copy-Item $settingsFile $backup
    Restrict-ToOwner $backup
    Write-Host "Replacing $settingsFile — the previous one is now $backup"
}

$settings = [ordered]@{
    env = [ordered]@{
        CLAUDE_CODE_USE_BEDROCK        = '1'
        AWS_REGION                     = $awsRegion
        ANTHROPIC_DEFAULT_SONNET_MODEL = $sonnetProfile
        ANTHROPIC_DEFAULT_OPUS_MODEL   = $opusProfile
        AWS_ACCESS_KEY_ID              = $accessKey
        AWS_SECRET_ACCESS_KEY          = $secretKey
    }
    model = 'sonnet'
    modelPicker = [ordered]@{
        options = @(
            [ordered]@{
                model       = $sonnetProfile
                behavesAs   = 'claude-sonnet-5'
                label       = 'Sonnet 5 (Inference profile)'
                description = 'EPM-CMBI Sonnet 5 Amazon Bedrock Application Inference Profile for Cost Tracking'
            },
            [ordered]@{
                model       = $opusProfile
                behavesAs   = 'claude-opus-5'
                label       = 'Opus 5 (Inference profile)'
                description = 'EPM-CMBI Opus 5 Amazon Bedrock Application Inference Profile for Cost Tracking'
            }
        )
        replaceBuiltInOptions = $true
    }
    effortLevel = 'high'
}

# Written without a byte-order mark: Windows PowerShell 5.1's -Encoding utf8 adds one, and a BOM
# makes the settings file unparseable.
[IO.File]::WriteAllText($settingsFile, ($settings | ConvertTo-Json -Depth 6),
    (New-Object Text.UTF8Encoding($false)))
Restrict-ToOwner $settingsFile

Write-Host "Wrote $settingsFile"
Write-Host "Next: run 'claude' from the repository root, then ask it to configure the development environment."
