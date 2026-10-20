$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$toolsRoot=Join-Path $projectRoot 'tools'
New-Item -ItemType Directory -Force -Path $toolsRoot | Out-Null
$archive=Join-Path $toolsRoot 'ffmpeg-release-essentials.zip'
$checksum=Join-Path $toolsRoot 'ffmpeg-release-essentials.zip.sha256'
if(!(Test-Path -LiteralPath $archive)){Invoke-WebRequest 'https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip' -OutFile $archive}
if(!(Test-Path -LiteralPath $checksum)){Invoke-WebRequest 'https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip.sha256' -OutFile $checksum}
$expected=((Get-Content -LiteralPath $checksum -Raw).Trim() -split '\s+')[0].ToLowerInvariant()
$actual=(Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
if($expected -ne $actual){throw 'FFmpeg archive checksum mismatch. Download the archive and checksum again before using it.'}
$unpack=Join-Path $toolsRoot 'ffmpeg-package'
Expand-Archive -LiteralPath $archive -DestinationPath $unpack -Force
$package=Get-ChildItem -LiteralPath $unpack -Directory | Where-Object {Test-Path -LiteralPath (Join-Path $_.FullName 'bin\ffmpeg.exe')} | Select-Object -First 1
if(!$package){throw 'No FFmpeg executable found in the verified archive.'}
$destination=Join-Path $toolsRoot 'ffmpeg'
New-Item -ItemType Directory -Force -Path $destination | Out-Null
Copy-Item -LiteralPath (Join-Path $package.FullName 'bin') -Destination $destination -Recurse -Force
if(Test-Path -LiteralPath (Join-Path $package.FullName 'LICENSE')){Copy-Item -LiteralPath (Join-Path $package.FullName 'LICENSE') -Destination $destination -Force}
& (Join-Path $destination 'bin\ffmpeg.exe') -version | Select-Object -First 1
Write-Output 'Verified media tools are ready in tools/ffmpeg/bin.'
