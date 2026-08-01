# Extension Playbook

Use this playbook when adding new behavior. Update README and the relevant `docs/` file in the same change.

## Add a New Operator or Service

Context: operator identity, supported hardware, route defaults, fares, transfer window, and backend URL live in `core:model`.

Steps:

1. Add enum value to `OperatorSubService`.
2. Add `OperatorConfig` in `OperatorPresets`.
3. Add mapping in `OperatorPresets.getPreset()`.
4. Add selection button/section in `SettingsScreen`.
5. Add or adapt dashboard presentation in `ValidatorMainScreen` if the brand needs a new visual identity.
6. Add tests for fare policy if payment behavior changes.
7. Update README operator list and `docs/ui_and_flows.md`.

Do not hard-code operator-specific payment rules inside Compose.

## Add a New Vendor Device

Context: vendor SDK must remain behind HAL.

Steps:

1. Add enum in `VendorDeviceModel` if missing.
2. Place SDK under `libs/vendor-sdk/<vendor>/`.
3. Add Gradle `compileOnly(files(...))` only in modules that import SDK classes.
4. Implement HAL interfaces in `core/hardware-drivers`.
5. Update `DeviceModelDetector.detectDeviceModel()`.
6. Update `VendorDriverFactory`.
7. Add unit tests for factory selection and adapter behavior.
8. Add target-device QA checklist to `docs/hardware_abstraction.md`.

Do not import vendor SDK in `feature:*`, `core:payment`, or `core:model`.

## Add a New Bank or Card Type

Context: card protocol belongs in `core:payment`; UI should only receive transaction state.

Steps:

1. Add `BankIssuer` enum if needed.
2. Implement `BankApduHandler`.
3. Register the handler in `BankApduManager`.
4. Add APDU helpers/constants if required.
5. Add deterministic unit tests for select/read/deduct.
6. Add end-to-end `PaymentEngine` test if business status can change.
7. Validate with real card, SAM, and vendor NFC adapter before field-ready status.
8. Update `docs/payment_engine.md`.

Preserve `PaymentEngine` guard order: mutex, time gate, anti-passback, fare, APDU, DB commit, feedback.

## Add a Database Field

Context: current database version is 1 and destructive migration is still enabled.

Steps:

1. Add field to `TransactionEntity` or a new entity.
2. Bump `@Database(version = ...)`.
3. Replace `fallbackToDestructiveMigration()` with explicit migrations before production.
4. Add migration tests.
5. Update sync payload contracts.
6. Update `docs/security_and_storage.md`.

Never rely on destructive migration for production validator data.

## Add Real Sync

Context: `SyncManager` currently marks transactions synced after simulated upload.

Steps:

1. Define backend request/response models in `core:network`.
2. Add idempotency contract using stable `transactionId` and/or `transCode`.
3. Implement upload in `SyncManager`.
4. Mark synced only after server acknowledgement.
5. Handle partial success.
6. Add retry/backoff policy, preferably WorkManager.
7. Add tests for empty queue, success, partial success, retryable failure, permanent failure.
8. Update `docs/security_and_storage.md`.

## Add Remote Command

Context: remote commands arrive through MQTT and are executed in `RemoteControlManager`.

Steps:

1. Define command name and typed payload.
2. Validate parameters.
3. Add allowlisted handler in `RemoteControlManager`.
4. Add logging without secrets.
5. Add signature/authentication before production use.
6. Add tests for valid, invalid, and unknown commands.
7. Update `docs/device_management.md`.

Do not add generic shell command execution from MQTT.

## Add a New Screen

Context: current routing is `MainActivity` enum-based.

Steps:

1. Decide whether the screen belongs in an existing feature module or a new `feature:<name>` module.
2. Keep composables state-driven.
3. Put business logic in ViewModel/use case/core service.
4. Add route state in `MainActivity` or introduce typed navigation if route count grows.
5. Add hardware interactions through HAL interfaces.
6. Update `docs/ui_and_flows.md`.

## Add a New Time Source

Context: `MultiSourceTimeSyncEngine` owns time confidence.

Steps:

1. Implement source collector at boundary module.
2. Normalize to trusted UTC milliseconds.
3. Call `validateAndUpdateTime(trustedUtcMs, source)`.
4. Persist checkpoint across reboot.
5. Add tests for skew, backward jump, source failure, and recovery.
6. Update `docs/time_validation.md`.

## Definition of Done for New Features

- Source code implemented in the correct module.
- No vendor SDK leak outside hardware/device-management layer.
- Unit tests or target-device QA evidence added according to risk.
- README status table updated.
- Matching `docs/` file updated.
- Known production gaps documented honestly.
