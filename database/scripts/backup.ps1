param([string]$SqlInstance='localhost',[string]$SqlServiceIdentity='NT SERVICE\MSSQLSERVER')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$backupRoot=Join-Path $projectRoot 'database\backups'
New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null
$currentIdentity=[Security.Principal.WindowsIdentity]::GetCurrent().Name
& icacls $backupRoot /inheritance:r /grant:r "${currentIdentity}:(OI)(CI)(F)" "${SqlServiceIdentity}:(OI)(CI)(M)" 'SYSTEM:(OI)(CI)(F)' | Out-Null
if($LASTEXITCODE -ne 0){throw 'Cannot restrict backup directory permissions.'}
$stamp=Get-Date -Format 'yyyyMMdd-HHmmss'
$destination=Join-Path $backupRoot $stamp
New-Item -ItemType Directory -Force -Path $destination | Out-Null
$backupFile=Join-Path $destination "TuiKhonApp-$stamp.bak"
$sqlFile=$backupFile.Replace("'","''")
$query="BACKUP DATABASE [TuiKhonApp] TO DISK=N'$sqlFile' WITH COPY_ONLY,CHECKSUM; RESTORE VERIFYONLY FROM DISK=N'$sqlFile' WITH CHECKSUM;"
$queryFile=Join-Path $destination 'backup-command.sql'
Set-Content -LiteralPath $queryFile -Value $query -Encoding Unicode
try{& sqlcmd -S $SqlInstance -E -C -b -i $queryFile}finally{Remove-Item -LiteralPath $queryFile}
if($LASTEXITCODE -ne 0){throw 'Backup or checksum verification failed. Check the SQL Server service identity and folder access.'}
$media=Join-Path $projectRoot 'storage\media'
if(Test-Path -LiteralPath $media){Copy-Item -LiteralPath $media -Destination $destination -Recurse}
Get-ChildItem -LiteralPath $destination -Recurse -File | Get-FileHash -Algorithm SHA256 | Select-Object Path,Hash | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $destination 'checksums.json')
Write-Output "Verified database and media backup: $destination"
