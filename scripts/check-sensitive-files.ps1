[CmdletBinding()]
param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$allowedEmailDomains = @(
    "example.com",
    "moaday.local",
    "moaday.test"
)
$allowedPlaceholderEmails = @(
    "your-account@gmail.com"
)
$textExtensions = @(
    ".css", ".env", ".html", ".java", ".js", ".json", ".md", ".mjs",
    ".properties", ".ps1", ".sh", ".sql", ".toml", ".ts", ".tsx", ".txt",
    ".xml", ".yaml", ".yml"
)
$emailPattern = "[A-Za-z0-9.!#$%&'*+/?^_``{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+"
$violations = [System.Collections.Generic.List[string]]::new()

Push-Location $RepositoryRoot
try {
    $trackedFiles = @(& git ls-files --cached --others --exclude-standard)
    if ($LASTEXITCODE -ne 0) {
        throw "git ls-files failed with exit code $LASTEXITCODE"
    }

    foreach ($file in $trackedFiles) {
        $path = $file.Replace("\", "/")
        $isEnvironmentFile = $path -match "(^|/)\.env($|\.)"
        $isEnvironmentExample = $path -match "(^|/)\.env(?:\.[^/]+)?\.example$"
        $isPrivateKey = $path -match "(?i)\.(pem|key|p12|pfx|jks|keystore)$"
        $isCredentialExport = $path -match "(?i)(^|/)(credentials(?:-[^/]+)?\.json|service-account[^/]*\.json|google-services\.json|GoogleService-Info\.plist)$"
        $isPrivateSshKey = $path -match "(?i)(^|/)(id_rsa|id_ed25519)(\.[^/]+)?$"
        $isSensitiveDirectory = $path -match "(?i)(^|/)(backups?|\.secrets?|secrets)(/|$)"

        if (($isEnvironmentFile -and -not $isEnvironmentExample) -or
            $isPrivateKey -or $isCredentialExport -or $isPrivateSshKey -or
            $isSensitiveDirectory) {
            $violations.Add("A forbidden sensitive file is included in the repository: $path")
            continue
        }

        $extension = [System.IO.Path]::GetExtension($path)
        $leafName = [System.IO.Path]::GetFileName($path)
        if ($textExtensions -notcontains $extension -and $leafName -notin @("Dockerfile", ".gitignore")) {
            continue
        }

        $content = Get-Content -LiteralPath $file -Raw -ErrorAction Stop
        foreach ($match in [regex]::Matches($content, $emailPattern)) {
            $email = $match.Value.ToLowerInvariant()
            $domain = $email.Split("@")[-1]
            if ($allowedEmailDomains -notcontains $domain -and
                $allowedPlaceholderEmails -notcontains $email) {
                $violations.Add("A non-placeholder email address is included in: $path")
                break
            }
        }
    }

    if ($violations.Count -gt 0) {
        $violations | Sort-Object -Unique | ForEach-Object { Write-Error $_ }
        throw "Sensitive file or personal email validation failed."
    }

    Write-Host "Sensitive file and personal email validation passed."
}
finally {
    Pop-Location
}
