#!/usr/bin/env bash
set -euo pipefail
# Only generated passwords are accepted; no SQL or shell interpolation of arbitrary input.
[[ "$DB_PASSWORD" =~ ^[A-Za-z0-9_!@%+=-]{24,72}$ ]] || { echo 'Use a generated runtime password (24-72 supported characters).' >&2; exit 1; }
[[ "$DB_MIGRATION_PASSWORD" =~ ^[A-Za-z0-9_!@%+=-]{24,72}$ ]] || { echo 'Use a generated migration password (24-72 supported characters).' >&2; exit 1; }
/opt/mssql-tools18/bin/sqlcmd -S sqlserver -U sa -C -b <<SQL
IF DB_ID(N'TuiKhonApp') IS NULL CREATE DATABASE [TuiKhonApp];
GO
USE [master];
IF SUSER_ID(N'tk_runtime_v2') IS NULL CREATE LOGIN [tk_runtime_v2] WITH PASSWORD=N'$DB_PASSWORD', CHECK_POLICY=ON, DEFAULT_DATABASE=[TuiKhonApp];
IF SUSER_ID(N'tk_migrator_v2') IS NULL CREATE LOGIN [tk_migrator_v2] WITH PASSWORD=N'$DB_MIGRATION_PASSWORD', CHECK_POLICY=ON, DEFAULT_DATABASE=[TuiKhonApp];
GO
USE [TuiKhonApp];
IF DATABASE_PRINCIPAL_ID(N'tk_runtime_v2') IS NULL CREATE USER [tk_runtime_v2] FOR LOGIN [tk_runtime_v2];
IF DATABASE_PRINCIPAL_ID(N'tk_migrator_v2') IS NULL CREATE USER [tk_migrator_v2] FOR LOGIN [tk_migrator_v2];
ALTER ROLE db_datareader ADD MEMBER tk_runtime_v2;
ALTER ROLE db_datawriter ADD MEMBER tk_runtime_v2;
ALTER ROLE db_owner ADD MEMBER tk_migrator_v2;
GO
SQL
