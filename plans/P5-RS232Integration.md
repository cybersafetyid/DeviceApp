# End-to-End Serial RS232 Integration

Kita akan mengimplementasikan dukungan `Serial RS232` secara komprehensif dari Hardware Abstraction Layer (HAL) ke *Application Layer* menggunakan SDK `com.lenz.e60qsdk.M3Serial`. 

## User Review Required

> [!IMPORTANT]
> **Use Case**: Apakah RS232 ini akan difokuskan untuk dihubungkan ke **Printer Tiket**, **Scanner Eksternal**, atau **Layar Penumpang (Passenger Display)**? 
> Jika untuk komunikasi dua arah yang terus-menerus (misal Scanner), saya akan menggunakan *Coroutine Flow / Channel* untuk *background listening*. Jika hanya untuk *Printing*, kita bisa buatkan struktur *Command-Response* satu arah.
>
> Saat ini, saya merancangnya dengan sistem *Full-Duplex Polling* menggunakan Kotlin Coroutines ( `Flow<ByteArray>` ) agar bisa digunakan untuk kedua skenario.

## Open Questions
- Berapa nilai *Baud Rate* standar yang akan digunakan? (Misalnya `9600` atau `115200`?)

## Proposed Changes

---

### `core:hardware-api`
Menambahkan abstraksi *driver* agar modul lain tidak langsung bergantung ke pustaka Lenz.

#### [NEW] [SerialDriver.kt](file:///Volumes/Gorby/AndroidStudioProjects/DeviceApp/core/hardware-api/src/main/java/com/enterprise/busvalidator/core/hardware/api/SerialDriver.kt)
- Mendefinisikan *interface* `SerialDriver`
- `fun open(baudRate: Int): Boolean`
- `fun close()`
- `fun sendData(data: ByteArray): Boolean`
- `val incomingData: Flow<ByteArray>`

---

### `core:hardware-drivers`
Implementasi nyata untuk Bus Validator E60 menggunakan `M3Serial`.

#### [NEW] [E60SerialAdapter.kt](file:///Volumes/Gorby/AndroidStudioProjects/DeviceApp/core/hardware-drivers/src/main/java/com/enterprise/busvalidator/core/hardware/drivers/E60SerialAdapter.kt)
- Membuat kelas yang meng-*implements* `SerialDriver` dan di-*inject* via Hilt.
- Melakukan pemanggilan `val m3Serial = M3Serial()` dan memanggil `.open(M3Serial.SERIAL_RS232, baudRate, M3Serial.BIT_8)`.
- Sebuah *background coroutine* akan me-*looping* dan membaca data via `m3Serial.recv(...)` untuk di-*emit* ke `Flow`.
- Memanfaatkan `m3Serial.send()` untuk pengiriman.

#### [MODIFY] [HardwareModule.kt](file:///Volumes/Gorby/AndroidStudioProjects/DeviceApp/core/hardware-drivers/src/main/java/com/enterprise/busvalidator/core/hardware/drivers/HardwareModule.kt)
- Menambahkan `@Provides` atau `@Binds` untuk *inject* `SerialDriver` sebagai *Singleton*.

---

### `feature:diagnostic`
Memastikan kapabilitas RS232 bisa diuji coba dari *Diagnostic Dashboard*.

#### [MODIFY] [HardwareDiagnosticViewModel.kt](file:///Volumes/Gorby/AndroidStudioProjects/DeviceApp/feature/diagnostic/src/main/java/com/enterprise/busvalidator/feature/diagnostic/HardwareDiagnosticViewModel.kt)
- Menambahkan `SerialDriver` di *constructor* Hilt.
- Pada saat diagnostik berjalan, sistem akan memanggil `.open(115200)`, mencoba mengirim data `PING`, dan mengembalikan status ke antarmuka pengguna.

## Verification Plan
### Automated Tests
- Menambahkan `E60SerialAdapterTest.kt` dengan `MockK` yang mensimulasikan aliran data (Flow) dari `M3Serial`.

### Manual Verification
- Melakukan kompilasi dan menjalankan *Hardware Diagnostic Screen* di perangkat E60.
- Mengecek status `Serial RS232 Port` apakah mendapatkan pesan *OK*.
