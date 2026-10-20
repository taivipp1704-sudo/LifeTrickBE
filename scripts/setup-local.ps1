param([string]$SqlInstance='localhost')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$envFile=Join-Path $projectRoot 'env\.env.local'
if(Test-Path -LiteralPath $envFile){Write-Output 'env/.env.local already exists. Reusing it; no password is rotated.';exit 0}
function New-TaskSecret { return [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))+'aA9!' }
$runtimePassword=New-TaskSecret;$migrationPassword=New-TaskSecret;$adminPassword=New-TaskSecret;$gatewaySecret=New-TaskSecret
$query=@"
IF DB_ID(N'TuiKhonApp') IS NULL CREATE DATABASE [TuiKhonApp];
GO
USE [master];
IF SUSER_ID(N'tk_runtime_v2') IS NOT NULL OR SUSER_ID(N'tk_migrator_v2') IS NOT NULL THROW 51000, 'Tui Khon SQL logins already exist. Reuse their environment configuration instead of resetting passwords.', 1;
CREATE LOGIN [tk_runtime_v2] WITH PASSWORD=N'$runtimePassword', CHECK_POLICY=ON, DEFAULT_DATABASE=[TuiKhonApp];
CREATE LOGIN [tk_migrator_v2] WITH PASSWORD=N'$migrationPassword', CHECK_POLICY=ON, DEFAULT_DATABASE=[TuiKhonApp];
GO
USE [TuiKhonApp];
CREATE USER [tk_runtime_v2] FOR LOGIN [tk_runtime_v2];
CREATE USER [tk_migrator_v2] FOR LOGIN [tk_migrator_v2];
ALTER ROLE [db_datareader] ADD MEMBER [tk_runtime_v2];
ALTER ROLE [db_datawriter] ADD MEMBER [tk_runtime_v2];
ALTER ROLE [db_owner] ADD MEMBER [tk_migrator_v2];
GO
"@
$query | & sqlcmd -S $SqlInstance -E -C -b
if($LASTEXITCODE -ne 0){throw 'SQL setup did not complete. Existing databases were not removed.'}
$value=@"
NODE_ENV=development
GATEWAY_HOST=127.0.0.1
GATEWAY_PORT=3000
API_BIND=127.0.0.1
API_PORT=8088
API_ORIGIN=http://127.0.0.1:8088
APP_ORIGINS=http://127.0.0.1:3000,http://localhost:3000
COOKIE_SECURE=false
GATEWAY_SECRET=$gatewaySecret
DB_URL=jdbc:sqlserver://localhost:1433;databaseName=TuiKhonApp;encrypt=true;trustServerCertificate=true
DB_USER=tk_runtime_v2
DB_PASSWORD=$runtimePassword
DB_MIGRATION_USER=tk_migrator_v2
DB_MIGRATION_PASSWORD=$migrationPassword
BOOTSTRAP_ADMIN_EMAIL=admin@tuikhon.local
BOOTSTRAP_ADMIN_PASSWORD=$adminPassword
SEED_CONTENT=true
MEDIA_ROOT=../storage/media
FFMPEG_PATH=../tools/ffmpeg/bin/ffmpeg.exe
FFPROBE_PATH=../tools/ffmpeg/bin/ffprobe.exe
"@
[IO.File]::WriteAllText($envFile,$value,[Text.UTF8Encoding]::new($false))
$currentIdentity=[Security.Principal.WindowsIdentity]::GetCurrent().Name
& icacls $envFile /inheritance:r /grant:r "${currentIdentity}:(F)" 'SYSTEM:(F)' | Out-Null
Write-Output 'Created isolated TuiKhonApp database and env/.env.local. Credentials were not printed. Read the admin credentials from that local file.'
