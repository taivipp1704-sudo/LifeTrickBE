-- Run while connected to the application database as the Azure SQL server admin.
-- Supply RuntimePassword and MigrationPassword as sqlcmd variables. Generate both
-- values locally and keep them out of source control and command history.

IF DATABASE_PRINCIPAL_ID(N'tk_runtime_v2') IS NULL
BEGIN
  EXEC(N'CREATE USER [tk_runtime_v2] WITH PASSWORD = ''' + REPLACE('$(RuntimePassword)', '''', '''''') + N'''');
END;

IF DATABASE_PRINCIPAL_ID(N'tk_migrator_v2') IS NULL
BEGIN
  EXEC(N'CREATE USER [tk_migrator_v2] WITH PASSWORD = ''' + REPLACE('$(MigrationPassword)', '''', '''''') + N'''');
END;

ALTER ROLE db_datareader ADD MEMBER tk_runtime_v2;
ALTER ROLE db_datawriter ADD MEMBER tk_runtime_v2;
ALTER ROLE db_owner ADD MEMBER tk_migrator_v2;

DENY ALTER, CONTROL, TAKE OWNERSHIP TO tk_runtime_v2;
