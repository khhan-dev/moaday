param(
    [Parameter(Mandatory = $true)]
    [string]$BackupDirectory,
    [switch]$ConfirmRestore
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
if (-not $ConfirmRestore) { throw "복원은 현재 로컬 데이터를 변경합니다. -ConfirmRestore 옵션을 함께 지정해 주세요." }

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$BackupDirectory = (Resolve-Path $BackupDirectory).Path
$DatabaseDump = Join-Path $BackupDirectory "database.dump"
$FilesArchive = Join-Path $BackupDirectory "files.tar.gz"
$Manifest = Join-Path $BackupDirectory "manifest.json"
foreach ($path in @($DatabaseDump, $FilesArchive, $Manifest)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "백업 파일이 없습니다: $path" }
}

Push-Location $ProjectRoot
try {
    $volume = docker volume ls --filter "label=com.docker.compose.project=moaday" --filter "label=com.docker.compose.volume=moaday-files" --format "{{.Name}}"
    if (-not $volume) { throw "MoaDay 첨부파일 볼륨을 찾을 수 없습니다. Docker 서비스를 먼저 실행해 주세요." }

    docker compose stop api web | Out-Host
    try {
        docker compose cp $DatabaseDump postgres:/tmp/moaday-restore.dump | Out-Host
        docker compose exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner /tmp/moaday-restore.dump'
        docker compose exec -T postgres rm -f /tmp/moaday-restore.dump

        docker run --rm --mount "type=volume,source=$volume,target=/data" --mount "type=bind,source=$BackupDirectory,target=/backup,readonly" alpine:3.22 tar -xzf /backup/files.tar.gz -C /data
    }
    finally {
        docker compose up -d api web | Out-Host
    }
    Write-Host "복원이 완료되었습니다: $BackupDirectory"
}
finally {
    Pop-Location
}
