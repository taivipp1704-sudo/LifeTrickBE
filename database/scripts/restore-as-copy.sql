-- Restore into a separate database for verification; never replace the live database here.
-- First inspect logical file names, then replace every placeholder below.
RESTORE FILELISTONLY FROM DISK=N'C:\SQLBackups\TuiKhonApp-example.bak';
-- RESTORE DATABASE [TuiKhonRestoreCheck] FROM DISK=N'C:\SQLBackups\TuiKhonApp-example.bak'
-- WITH MOVE N'TuiKhonApp' TO N'C:\SQLData\TuiKhonRestoreCheck.mdf',
-- MOVE N'TuiKhonApp_log' TO N'C:\SQLData\TuiKhonRestoreCheck_log.ldf',CHECKSUM,RECOVERY;
-- DBCC CHECKDB([TuiKhonRestoreCheck]) WITH NO_INFOMSGS;
