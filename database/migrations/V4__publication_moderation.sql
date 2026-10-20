ALTER TABLE published_tips ADD visible bit NOT NULL DEFAULT 1;
CREATE TABLE moderation_actions(id varchar(36) PRIMARY KEY,tip_id varchar(80) NOT NULL REFERENCES published_tips(id),actor_id varchar(36) NOT NULL REFERENCES app_users(id),action varchar(24) NOT NULL,reason nvarchar(1500) NOT NULL,created_at datetime2 NOT NULL DEFAULT SYSUTCDATETIME());
IF DATABASE_PRINCIPAL_ID('tk_runtime_v2') IS NOT NULL BEGIN DENY UPDATE,DELETE ON moderation_actions TO tk_runtime_v2; END;
