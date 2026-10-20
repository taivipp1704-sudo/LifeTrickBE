DECLARE @uniqueName sysname;
SELECT @uniqueName=kc.name FROM sys.key_constraints kc WHERE kc.parent_object_id=OBJECT_ID('dbo.published_tips') AND kc.type='UQ';
IF @uniqueName IS NOT NULL BEGIN DECLARE @sql nvarchar(max); SET @sql='ALTER TABLE dbo.published_tips DROP CONSTRAINT '+QUOTENAME(@uniqueName); EXEC sp_executesql @sql; END;
CREATE UNIQUE INDEX IX_published_contribution ON published_tips(contribution_id) WHERE contribution_id IS NOT NULL;
