# Cross-Platform File Encryption and Sync Utility

Java implementation of a secure folder sync utility that encrypts files before backup, tracks incremental changes with SHA-256, deduplicates identical content, and can run as a background daemon with a small REST control API.

## Features

- AES-256-GCM authenticated encryption before data leaves the source folder
- PBKDF2-HMAC-SHA256 key derivation with per-object salt
- SHA-256 content hashing for incremental diff checks and deduplication
- SQLite manifest that maps original paths to encrypted backup objects
- Background daemon mode with configurable sync interval
- REST API using Java's built-in HTTP server
- Restore command that decrypts files back into their original relative paths

## Build

Requires JDK 17+ and Maven.

```powershell
mvn package
```

The runnable jar is created at:

```text
target/secure-file-sync-1.0.0.jar
```

## Quick Start

Set a password in an environment variable:

```powershell
$env:SECURE_SYNC_PASSWORD = "replace-with-a-long-random-passphrase"
```

Run a one-time sync:

```powershell
java -jar target/secure-file-sync-1.0.0.jar sync `
  --source ./sample-data `
  --backup ./backup `
  --db ./secure-sync.db `
  --password-env SECURE_SYNC_PASSWORD
```

Run as a background daemon with REST API on port 8080:

```powershell
java -jar target/secure-file-sync-1.0.0.jar daemon `
  --source ./sample-data `
  --backup ./backup `
  --db ./secure-sync.db `
  --password-env SECURE_SYNC_PASSWORD `
  --interval-seconds 60 `
  --api-port 8080
```

Restore encrypted files:

```powershell
java -jar target/secure-file-sync-1.0.0.jar restore `
  --backup ./backup `
  --db ./secure-sync.db `
  --target ./restore `
  --password-env SECURE_SYNC_PASSWORD
```

## REST API

When daemon mode starts with `--api-port 8080`, these endpoints are available:

```text
GET  /health
GET  /files
POST /sync
```

Example:

```powershell
Invoke-RestMethod -Method Post http://localhost:8080/sync
```

## How Sync Works

1. The source folder is scanned recursively.
2. Each regular file gets a SHA-256 hash.
3. If the hash is already present in the SQLite manifest, the backup object is reused.
4. New content is encrypted with AES-256-GCM using a PBKDF2-derived key.
5. The encrypted object is stored under `backup/objects/<prefix>/<sha256>.sfs`.
6. Deleted source files are removed from the manifest, and unreferenced encrypted objects are cleaned up.
