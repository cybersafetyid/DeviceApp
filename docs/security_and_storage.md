# Security, Storage & Sync Engine

## Encrypted Database (`:core:database`)
All sensitive local data is protected using SQLCipher to encrypt the Room Database with AES-256.
- **Transaction Ledger:** Every payment is written to the ledger immediately. This acts as the source of truth for offline settlements.
- **Time Checkpoints:** Persists the `last_valid_utc_timestamp` to prevent time tampering across reboots.

## Encrypted Logging (`:core:security`)
Plaintext logs are a security vulnerability, as they may leak APDU traces, Card UIDs, or SAM responses.
- The `EncryptedLogger` intercepts all application logs and writes them as an AES-256-GCM encrypted binary stream to the local file system.
- Logs can only be decrypted at the depot or on the server using the corresponding private key.

## Offline-First Auto Sync (`:core:sync`)
Buses frequently travel through cellular dead zones. The sync engine is built on an offline-first philosophy using Kotlin Coroutines and WorkManager.
- **Batched Uploads:** Transactions are queued locally and uploaded in batches when a stable network is detected.
- **Idempotent Retries:** Network failures automatically trigger exponential backoff retries.
- **Scheduled Backups:** At the end of the shift (e.g., 2:00 AM), a scheduled task compresses the encrypted database and logs into a secure archive and uploads it to the backend infrastructure.
