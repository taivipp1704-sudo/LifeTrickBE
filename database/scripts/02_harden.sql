USE [TuiKhonApp];
DENY UPDATE, DELETE ON dbo.audit_events TO tk_runtime_v2;
DENY UPDATE, DELETE ON dbo.review_decisions TO tk_runtime_v2;
DENY UPDATE, DELETE, INSERT ON dbo.flyway_schema_history TO tk_runtime_v2;
