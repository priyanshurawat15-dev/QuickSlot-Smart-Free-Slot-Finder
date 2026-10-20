# Deployment and recovery

This configuration targets a single Linux host with Docker Compose. It is a deployment starting point, not a tested production rollout. Only the web proxy should publish a port; PostgreSQL and API stay on the private network. Enable HTTPS before using real accounts. Never seed demo accounts into a university deployment.

## Before deployment

Set a strong database password outside Git, configure the site hostname, use secure cookies, review uploaded-file storage permissions, and run migrations. Source documents are private database records; downloaded public research files are not automatically imported. Test the full backup/restore procedure on a separate database before accepting real bookings.

Copy .env.example to a private .env file and set POSTGRES_PASSWORD and SITE_ADDRESS. Use the approved DNS hostname so Caddy can obtain HTTPS certificates. Then run from the repository root:

```powershell
docker compose --env-file .env -f infra/compose.yml up --build -d
```

The API uses JDBC configuration internally. Database and API ports are not published. This command has not been executed on this host because Docker is unavailable.

## Backups

Use pg_dump in custom format for the database; retained source documents are included in the database dump. Store encrypted backups outside the application host. Keep access restricted. Verify retained source hashes and publication history after restoration.

Example from a trusted database administration environment:

```text
pg_dump --format=custom --file=quickslot-backup.dump "$DATABASE_URL"
pg_restore --exit-on-error --no-owner --dbname="$RESTORE_DATABASE_URL" quickslot-backup.dump
```

Use native PostgreSQL connection URLs for pg_dump, not JDBC URLs starting jdbc:. Configure credentials via a protected service/password file rather than command-line passwords. Restore into a new isolated database; never run a restore over the active application database as a test.

## Release checks

Run PostgreSQL integration tests and web build. Confirm a student cannot book, an administrator cannot approve without authority, two competing approvals cannot both succeed, and an old timetable replacement preserves other scopes. Verify secure session cookies behind the proxy and no default accounts. Exercise the import review and publication workflow using verified campus sources. Record software versions, dataset coverage and observed response times.

## Failure handling

Database outage must stop confirmations. Unverified or missing schedules remain unknown. Search results are schedule-based; users must check the physical room before entering. Never remove old occupancy simply because a timetable source is unreachable. Cache and notification failures must not silently rewrite decisions.
