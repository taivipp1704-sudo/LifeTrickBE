[CmdletBinding()]
param(
  [string]$OutputPath = (Join-Path $PSScriptRoot "../env/.env.cloud.local")
)

$ErrorActionPreference = "Stop"

function New-UrlSafeSecret([int]$Bytes = 36) {
  $buffer = [System.Security.Cryptography.RandomNumberGenerator]::GetBytes($Bytes)
  return [Convert]::ToBase64String($buffer).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

$values = [ordered]@{
  AZURE_SQL_ADMIN_USER = "lifetrick_owner"
  AZURE_SQL_ADMIN_PASSWORD = New-UrlSafeSecret 36
  DB_USER = "tk_runtime_v2"
  DB_PASSWORD = New-UrlSafeSecret 36
  DB_MIGRATION_USER = "tk_migrator_v2"
  DB_MIGRATION_PASSWORD = New-UrlSafeSecret 36
  GATEWAY_SECRET = New-UrlSafeSecret 48
  BOOTSTRAP_ADMIN_EMAIL = "admin@lifetrick.local"
  BOOTSTRAP_ADMIN_PASSWORD = New-UrlSafeSecret 36
}

$lines = @(
  "# Local deployment secrets. Never commit or paste this file into chat."
  "# DB_URL and the Azure SQL host are added after the free database is created."
)
$lines += $values.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }

$directory = Split-Path -Parent $OutputPath
[System.IO.Directory]::CreateDirectory($directory) | Out-Null
[System.IO.File]::WriteAllLines(
  [System.IO.Path]::GetFullPath($OutputPath),
  $lines,
  [System.Text.UTF8Encoding]::new($false)
)

Write-Output "Created local cloud secret file at $([System.IO.Path]::GetFullPath($OutputPath))"
