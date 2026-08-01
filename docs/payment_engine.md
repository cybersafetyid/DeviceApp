# Payment Engine & Fare Rules

The core responsibility of the validator is to securely process payments offline and ensure funds are successfully settled to acquiring banks.

## Zero-Loss Offline-First Payment Engine (`:core:payment`)
Transactions are executed in an **Atomic 6-step APDU pipeline**. If any step fails (e.g., the passenger pulls the card away too fast), the system guarantees a rollback without corrupting the card's balance.

1. **Card Detection & Polling:** HAL triggers on NFC tag discovery.
2. **Mutual Authentication:** SAM module authenticates the card via cryptographic keys.
3. **Read Balance & History:** Check if the card has sufficient balance and inspect the last transaction.
4. **Time Confidence Gate Check:** Ensure the system time is SECURE_SYNCED before writing to the card.
5. **Deduct (Write):** Send the deduct APDU command.
6. **Commit & Log:** Write the encrypted transaction record to the Room Database and increment the daily counter.

## Double Deduct Safeguard & Anti-Passback
To prevent a passenger from being charged twice accidentally (or sharing a card out the window):
- **Anti-Passback Guard:** Maintains an in-memory and database cache of recently tapped Card UIDs.
- If a UID is tapped twice within the configured timeout (e.g., 5 minutes), the state transitions to `CardAlreadyTappedState` and plays a distinct warning sound, **without** deducting the fare again.

## Dynamic Intermodal Fare & Promo Engine
The fare is not static; it dynamically calculates based on multiple variables downloaded during initialization.

- **Base Fare:** e.g., Rp 3.500.
- **Time-based Promos:** e.g., Off-peak hours cost Rp 2.000.
- **Passenger Profiles:** Special cards flagged as Lansia (Elderly), Student, or PNS receive subsidized rates (e.g., Rp 0).
- **Intermodal Integration:** If the card's transaction history shows a recent tap at an MRT or LRT station within the last 45 minutes, a transfer discount is automatically applied.

## Double Fare Validation Safeguard
A dual-layer check to prevent mathematically impossible deductions:
- **Layer 1:** SAM module / Card internal limits.
- **Layer 2:** Application-level mathematical bounds checking. If the calculated fare exceeds the maximum allowed tariff (e.g., > Rp 20.000), the transaction is forcefully aborted to protect the passenger.
