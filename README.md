# 🔐 Cross-Platform File Encryption & Sync Utility

A secure, cross-platform file synchronization utility built in **Java** that encrypts files before backing them up, tracks incremental changes using SHA-256, stores metadata in SQLite, and exposes a lightweight REST API for monitoring and synchronization.

---

## 🚀 Features

- 🔒 AES-256-GCM authenticated encryption
- 🔑 PBKDF2-HMAC-SHA256 key derivation
- 📁 Incremental synchronization
- 🔄 SHA-256 content hashing & deduplication
- 🗄 SQLite metadata manifest
- ⚡ Background daemon mode
- 🌐 Lightweight REST API
- ♻ Secure file restoration
- 🐳 Docker support
- 💻 Cross-platform (Windows/Linux)

---

## 🏗 Tech Stack

| Technology | Purpose |
|------------|---------|
| Java 21 | Backend |
| Maven | Dependency Management |
| SQLite | Metadata Storage |
| Java HTTP Server | REST API |
| Docker | Containerization |
| AES-256-GCM | File Encryption |
| PBKDF2 | Password-based Key Derivation |
| SHA-256 | Incremental Sync & Deduplication |

---

## 📂 Project Structure

```
src/
 ├── api/
 ├── crypto/
 ├── model/
 ├── restore/
 ├── storage/
 ├── sync/
 ├── util/
 └── App.java

Dockerfile
pom.xml
README.md
```

---

# Build

Requirements

- Java 17 or above
- Maven 3.9+

```bash
mvn clean package
```

Generated JAR

```
target/secure-file-sync-1.0.0.jar
```

---

# Running the Application

## One-Time Sync

```powershell
$env:SECURE_SYNC_PASSWORD="replace-with-a-long-random-password"

java -jar target/secure-file-sync-1.0.0.jar sync `
--source ./sample-data `
--backup ./backup `
--db ./secure-sync.db `
--password-env SECURE_SYNC_PASSWORD
```

---

## Daemon Mode

Runs continuously and exposes a REST API.

```powershell
java -jar target/secure-file-sync-1.0.0.jar daemon `
--source ./sample-data `
--backup ./backup `
--db ./secure-sync.db `
--password-env SECURE_SYNC_PASSWORD `
--interval-seconds 60 `
--api-port 8080
```

---

## Restore Files

```powershell
java -jar target/secure-file-sync-1.0.0.jar restore `
--backup ./backup `
--db ./secure-sync.db `
--target ./restore `
--password-env SECURE_SYNC_PASSWORD
```

---

# Docker

Build

```bash
docker build -t secure-sync .
```

Run

```bash
docker run -p 8080:8080 secure-sync
```

---

# REST API

## Health Check

```
GET /health
```

Response

```json
{
  "status":"ok"
}
```

---

## List Synced Files

```
GET /files
```

Returns metadata of synchronized files.

---

## Trigger Sync

```
POST /sync
```

Example

```powershell
Invoke-RestMethod -Method POST http://localhost:8080/sync
```

---

# How Synchronization Works

1. Recursively scans the source directory.
2. Calculates SHA-256 for every file.
3. Checks the SQLite manifest for duplicates.
4. Encrypts new files using AES-256-GCM.
5. Stores encrypted objects inside the backup directory.
6. Updates the SQLite manifest.
7. Removes orphaned encrypted files.

---

# Security

- AES-256-GCM authenticated encryption
- PBKDF2-HMAC-SHA256 password-based key derivation
- Per-file random salt
- Integrity verification during decryption
- Safe path validation during restore

---

# Future Improvements

- Web Dashboard
- JWT Authentication
- File Versioning
- Cloud Storage Integration (AWS S3, Azure Blob)
- Email Notifications
- Multi-user Support

---

# Author

**Vyshnavi G**


