param(
    [string]$OutputDirectory = (Join-Path $PSScriptRoot ("..\backups\moaday-" + (Get-Date -Format "yyyyMMdd-HHmmss")))
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$OutputDirectory = [System.IO.Path]::GetFullPath($OutputDirectory)

Push-Location $ProjectRoot
try {
    New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
    $volume = docker volume ls --filter "label=com.docker.compose.project=moaday" --filter "label=com.docker.compose.volume=moaday-files" --format "{{.Name}}"
    if (-not $volume) { throw "MoaDay 첨부파일 볼륨을 찾을 수 없습니다. Docker 서비스를 먼저 실행해 주세요." }

    docker compose stop api web | Out-Host
    try {
        docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --file=/tmp/moaday-backup.dump'
        docker compose cp postgres:/tmp/moaday-backup.dump (Join-Path $OutputDirectory "database.dump") | Out-Host
        docker compose exec -T postgres rm -f /tmp/moaday-backup.dump

        docker run --rm --mount "type=volume,source=$volume,target=/data,readonly" --mount "type=bind,source=$OutputDirectory,target=/backup" alpine:3.22 tar -czf /backup/files.tar.gz -C /data .
        [ordered]@{
            product = "MoaDay"
            createdAt = (Get-Date).ToUniversalTime().ToString("o")
            database = "database.dump"
            attachments = "files.tar.gz"
            composeProject = "moaday"
        } | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $OutputDirectory "manifest.json")
    }
    finally {
        docker compose up -d api web | Out-Host
    }
    Write-Host "백업이 완료되었습니다: $OutputDirectory"
}
finally {
    Pop-Location
}
