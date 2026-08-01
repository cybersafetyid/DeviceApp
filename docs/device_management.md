# Device Management & Self-Healing

Enterprise validators operate unattended in harsh environments. They must recover from software faults, network drops, and memory leaks automatically.

## AppHealthWatchdog (Anti-Hang Engine)
Located in `:core:devicemanager`, the Watchdog continuously monitors application health.
- **Heartbeat:** Background coroutines emit a heartbeat every 3 seconds.
- **Deadlock Detection:** If the main UI thread or critical IO threads freeze (e.g., due to a native driver crash), the heartbeat stops.
- **Auto-Recovery:** If no heartbeat is detected for 10 seconds, the Watchdog triggers a ruthless restart. Depending on device capabilities, it may use standard intent restarts, or if rooted, execute `su -c reboot` to completely reset the hardware state.

## Remote Management Engine
Maintains a persistent, lightweight MQTT connection for telemetry and remote control.
- **Telemetry:** Streams GPS coordinates, battery level, temperature, and HAL health status.
- **Remote Commands:** Can receive securely signed payloads to execute actions:
  - `cmd_reboot`: Force reboot the device.
  - `cmd_restart_app`: Restart the application.
  - `cmd_fetch_logs`: Trigger an immediate upload of encrypted logs to the server.
  - `cmd_update_config`: Force a re-fetch of parameters (promos, fares, routes).
  - `cmd_remote_screen_capture`: Capture and upload a screenshot for debugging.

## Root-Assisted Silent OTA (Over-The-Air) Updates
To avoid manual intervention by depot staff, APK updates are downloaded in the background.
Once downloaded and checksum verified, the `SuManager` executes silent package installation:
`su -c pm install -r /path/to/update.apk`
followed by an automatic restart of the intent.

## Interactive Initialization SplashScreen
Upon boot, the device must verify its integrity before opening the payment gate.
The SplashScreen displays a sequential progress checklist:
1. Load Keystore.
2. Initialize Encrypted Database.
3. Mount Hardware HAL (NFC, SAM, Scanner).
4. Fetch Terminal Parameters (MID, TID, SAM_ID, TAP_MODE) from the server.
5. Synchronize NTP/GPS Time.
6. Run Health Diagnostics.
Only upon 100% success does it transition to the Main Validator UI.
