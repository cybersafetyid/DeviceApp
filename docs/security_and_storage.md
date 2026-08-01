# Security, Storage, and Sync

## Implemented Security Components

| Component | File | Current behavior |
| --- | --- | --- |
| `EncryptedLogger` | `core/security/EncryptedLogger.kt` | Writes AES/GCM encrypted Base64 log lines to `filesDir/encrypted_logs`. |
| `LogAndDbDecryptor` | `core/security/LogAndDbDecryptor.kt` | JVM utility object to decrypt encrypted log lines/files and database backup payloads. |
| `NativeSecurityVault` | `core/security/NativeSecurityVault.kt` | Obfuscates a fallback base URL and Base64-encodes outgoing payload strings. |
| `SuManager` | `core/security/SuManager.kt` | Executes root commands for reboot, silent APK install, system time set, and generic command execution. |
| `RuntimePermissionProvisioner` | `core/security/RuntimePermissionProvisioner.kt` | Attempts runtime permission grants and system provisioning via root, with Device Owner fallback for selected actions. |
| `MultiSourceTimeSyncEngine` | `core/security/MultiSourceTimeSyncEngine.kt` | Monotonic drift/time confidence logic. See `docs/time_validation.md`. |

## Database

`core/database` uses Room with SQLCipher:

- Database name: `bus_validator_encrypted.db`
- Entity: `TransactionEntity`
- DAO: `TransactionDao`
- Hilt module: `DatabaseModule`

The SQLCipher passphrase is currently hard-coded:

```text
EnterpriseBusValidatorSQLCipherPassphrase2026
```

This is acceptable only for local foundation work. Before production:

1. Move key material to Android Keystore, vendor secure storage, injected provisioning, or secure element.
2. Remove `fallbackToDestructiveMigration()`.
3. Add explicit Room migrations and migration tests.
4. Decide whether `exportSchema` should be enabled for migration review.

## Transaction Ledger

The local ledger is the offline source of truth. A transaction is inserted immediately after payment success:

```mermaid
flowchart LR
    PaymentEngine --> TransactionRecord
    TransactionRecord --> TransactionEntity
    TransactionEntity --> Room
    Room --> SQLCipher
    SQLCipher --> SyncManager
```

DAO capabilities:

- Insert/replace transaction.
- Get unsynced transactions ordered by timestamp.
- Mark transaction IDs as synced.
- Observe pending sync count.
- Observe daily transaction count.
- Read latest transaction.
- Insert GPS/network location logs.
- Read undelivered location logs for MQTT/API retry.
- Mark location logs delivered or failed.
- Prune location logs older than 7 days.

## Logs and Backup

`EncryptedLogger`:

- Uses AES/GCM/NoPadding.
- Prepends random 12-byte IV to each encrypted log entry.
- Writes one Base64 encrypted entry per line.
- Groups files by `timestamp / 86400000`.

`DatabaseBackupManager`:

- Reads `bus_validator_encrypted.db`.
- Encrypts raw DB bytes with AES-GCM.
- Writes `filesDir/backups/db_backup_<timestamp>.db.enc`.
- Can decrypt backup files and log lines.

Current AES key is hard-coded:

```text
EnterpriseBusValidatorAESKey2026
```

Replace it before production.

## Sync

`SyncManager` implements ACK-driven offline-first transaction sync:

1. Read successful unsynced rows from `TransactionDao.getUnsyncedTransactions()`.
2. Build a deterministic batch ID from device ID, first/last counter, and transaction IDs.
3. POST `/transactions/sync` with `Idempotency-Key`.
4. Require the backend to return exact `acceptedTransactionIds` and `backendLastCounter`.
5. Mark rows synced only when every local transaction ID is accepted and `backendLastCounter == localLastSuccessCounter`.
6. Store `device_counter_state.syncConflictReason` and block future successful commits when the backend response is partial, conflicting, or counter-mismatched.

Expected success response shape:

```json
{
  "acceptedTransactionIds": ["tx-1", "tx-2"],
  "backendLastCounter": 42
}
```

Expected conflict response shape:

```json
{
  "acceptedTransactionIds": ["tx-1"],
  "backendLastCounter": 41,
  "conflictReason": "backend counter mismatch"
}
```

The backend must treat `transactionId` and `Idempotency-Key` as idempotency keys, and `transactionCounter` as the monotonic per-device success-ledger counter. Duplicate retry of the same batch must return the same accepted IDs and same backend last counter.

It does not yet persist detailed sync attempt history or schedule retries through WorkManager.

`androidx.work:work-runtime-ktx` is present in the Gradle dependencies, so WorkManager can be added without changing the version catalog.

`TelemetrySyncManager` is already automatic at application startup:

1. Starts location tracking and the MQTT reconnect loop.
2. Persists each location snapshot to `location_logs`.
3. Publishes the full location snapshot to MQTT with QoS 1.
4. Falls back to API upload when MQTT is disconnected or publish fails.
5. Retries undelivered logs every 30 seconds.
6. Keeps location logs for 7 days before pruning.

## Required Production Hardening

- Replace hard-coded cryptographic keys and URLs.
- Add real HMAC/signature for `TransactionRecord.recordSignature`.
- Encrypt or redact card UID/log-sensitive fields based on settlement requirements.
- Define backend idempotency contract for `transactionId` and `transCode`.
- Add migration tests before changing DB schema.
- Add sync tests for empty queue, success, partial success, retryable failure, permanent failure.
