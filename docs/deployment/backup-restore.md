# Backup and Restore

FamilyAgent backups contain a PostgreSQL custom-format dump and a MinIO bucket mirror. Both artifacts are encrypted with AES-256-CBC/PBKDF2 and checksummed before retention or offsite copy.

## Configure

```bash
cp deploy/examples/backup.env.example .env.backup
sudo install -d -m 700 /etc/familyagent /var/backups/familyagent
openssl rand -base64 48 | sudo tee /etc/familyagent/backup-passphrase >/dev/null
sudo chmod 600 /etc/familyagent/backup-passphrase
```

Set `BACKUP_OFFSITE_ROOT` to a separately mounted filesystem or replicated destination. Keep the passphrase outside all backup destinations and copy it to a controlled disaster-recovery secret store.

## Run a Backup

```bash
set -a
source .env.backup
set +a
bash scripts/backup-stack.sh
```

Each timestamped backup directory contains:

- `postgresql.dump.enc`
- `minio.tar.gz.enc`
- `SHA256SUMS`
- `manifest.txt`

The script deletes local timestamp directories older than `BACKUP_RETENTION_DAYS`. Offsite retention should be controlled independently.

## Schedule

Create the `familyagent-backup` service account, grant it read access to the repository and passphrase, and add it to the Docker group. Then install the supplied systemd units:

```bash
sudo cp deploy/systemd/familyagent-backup.* /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now familyagent-backup.timer
sudo systemctl list-timers familyagent-backup.timer
```

Review every run:

```bash
sudo journalctl -u familyagent-backup.service --since today
```

## Restore Drill

Restore into a temporary environment first. The command stops the production application services, replaces PostgreSQL data, overwrites matching MinIO objects, and then starts the application again.

```bash
set -a
source .env.backup
set +a
bash scripts/restore-stack.sh /var/backups/familyagent/20260802T023000Z --confirm
```

The MinIO restore does not delete additional destination objects. After restoration, verify:

- registration and login;
- old memory queries, edits, and deletes;
- personal versus family visibility and unauthorized exclusion;
- file and image downloads;
- Flyway schema history and application health.

Record the restore duration, failed checks, and the latest recoverable timestamp. Run this drill on a regular schedule rather than treating a successful backup command as proof of recoverability.
