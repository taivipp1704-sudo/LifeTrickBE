# Túi Khôn

Apply the installed `.codex/skills/taste-skill` and `frontend-ui-engineering` skills for interface changes. Apply `writing-user-docs` for setup and usage documentation. Preserve the existing forest/neutral design system.

- Keep `FE/`, `BE/`, `NodeGateway/`, `database/`, `env/`, and `deployment/` separate.
- MVC2: controllers handle HTTP contracts, services enforce business rules, repositories execute parameterized SQL. React views use controllers/hooks and API models.
- Learn from `D:/DemoReview` without modifying it or copying its credentials.
- Do not print credentials, session cookies, CSRF tokens, or `.env.local` contents. Keep real environment files, media, backups, logs, and test artifacts out of Git and release ZIPs.
- Review decisions must come from a different authorized person, preserve evidence and snapshots, and be auditable. A completeness score is not a safety verdict.
- Run meaningful SQL Server API integration tests and browser tests after auth, media, or moderation changes. Report deployment limits accurately; the static Sites demo cannot host Spring Boot/SQL Server.
