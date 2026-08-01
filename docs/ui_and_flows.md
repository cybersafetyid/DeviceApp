# UI and User Flows

## Jetpack Compose & State-Screen Architecture
The application UI (`:feature:validator`) is built entirely with Jetpack Compose.
- **No Popups:** Dialogs and popups can cause focus loss on devices without touchscreens. All alerts, errors, and successes are represented as full-screen State transitions.
- **MVI (Model-View-Intent):** The UI observes state from the ViewModel and issues intents for user actions (like keypad presses).

## International Transit Standard Dashboard (VDV / ITxPT Layout)
The main validator screen adheres to standard public transit layout principles for rapid visual acquisition by the driver and passenger.

- **Header Bar (Live Status):**
  - Current Clock (Sync Status color-coded).
  - Bus Code & Active Route.
  - GPS 3D Fix Indicator.
  - SIM 4G Signal Strength.
  - Online/Offline Badge.
  - Hardware HAL Status (`RS232: OK`, `SAM: OK`, `NFC: OK`).
- **Main Body:**
  - Base Fare Display.
  - Tap Card / Scan QR Instruction Animation.
- **Footer Bar:**
  - Today's Passenger Counter.
  - Last Transaction Summary (Success/Failed).
  - Pending Sync Queue Count.
  - App Version.

## Physical Keypad Navigation
Many enterprise validators lack touch screens, relying entirely on physical buttons.
- The `KeypadDriver` maps physical GPIO or USB keyboard inputs to standard events: `Up`, `Down`, `Enter`, `Esc`.
- Compose UI elements implement custom focus management to navigate lists, configuration screens, and menus using strictly these four keys.

## Diagnostic Screens (`:feature:diagnostic`)
Accessible via a secret key combination or admin card tap, providing hardware self-test screens for:
- NFC Antenna Strength.
- SAM Module Connection.
- Serial Port Loopback Test.
- Scanner Read Test.
- Audio and LED Output.
- GPS Satellite Fix Data.
- MQTT Connection Ping.
