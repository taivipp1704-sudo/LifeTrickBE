#!/usr/bin/env bash
set -euo pipefail
export SQLCMDPASSWORD="${SQLCMDPASSWORD:-${MSSQL_SA_PASSWORD:?SQL administrator password required}}"
# Run inside the SQL container with SQLCMDPASSWORD supplied as an environment secret.
stamp="$(date -u +%Y%m%d-%H%M%S)"
file="/var/opt/mssql/backups/TuiKhonApp-$stamp.bak"
mkdir -p /var/opt/mssql/backups
/opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -b -Q "BACKUP DATABASE [TuiKhonApp] TO DISK=N'$file' WITH COPY_ONLY,CHECKSUM; RESTORE VERIFYONLY FROM DISK=N'$file' WITH CHECKSUM;"
echo "Verified SQL backup: $file"
