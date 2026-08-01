# Implementation Plan - Dynamic App Versioning & Multi-Operator Support

Implement automatic dynamic versioning in Gradle and UI, and multi-operator support for Biskita, Citra, and Surabaya with distinct UI visual identities, distinct **Base URLs**, and custom **Fare Rules** for each operator/service.

## User Review Required

> [!IMPORTANT]
> **Operator, Base URL, Fare Rules & Device Matrix**:
>
> 1. **Biskita**:
>    - **Bekasi** (E60Q, E60V2)
>      - **Base URL**: `https://api.biskita-bekasi.transindo.id/v1`
>      - **Fare**: Rp 4.000 (General), Rp 2.000 (Student), Rp 0 (Senior/Disabled).
>    - **Depok** (E60V2)
>      - **Base URL**: `https://api.biskita-depok.transindo.id/v1`
>      - **Fare**: Rp 3.500 (General), Rp 2.000 (Student), Rp 0 (Senior/Disabled).
>    - **Bogor** (E60Q, E60V2)
>      - **Base URL**: `https://api.biskita-bogor.transindo.id/v1`
>      - **Fare**: Rp 4.000 (General), Rp 2.000 (Student), Rp 0 (Senior/Disabled).
>
> 2. **Citra**:
>    - **Citra Raya** (E60Q, E60V2)
>      - **Base URL**: `https://api.citraraya-shuttle.co.id/v1`
>      - **Fare**: Rp 5.000 (General), Rp 3.000 (Resident/Student), Rp 2.500 (Senior).
>    - **Citra Maja** (E60Q, E60V2)
>      - **Base URL**: `https://api.citramaja-shuttle.co.id/v1`
>      - **Fare**: Rp 5.000 (General), Rp 3.000 (Resident/Student), Rp 2.500 (Senior).
>
> 3. **Surabaya**:
>    - **Wara Wiri** (E60Q)
>      - **Base URL**: `https://api-warawiri.surabaya.go.id/v1`
>      - **Fare**: Rp 5.000 (General Feeder), Rp 2.500 (Student), Rp 0 (Senior/Disabled).
>    - **Bus Surabaya (Suroboyo Bus)** (Q6)
>      - **Base URL**: `https://api-suroboyobus.surabaya.go.id/v1`
>      - **Fare**: Rp 5.000 (General Bus), Rp 2.500 (Student/Veteran), Rp 0 (Senior/Disabled). Integrated 30-min transfer policy.

## Proposed Changes

---

### Gradle & Core Common (`:app`, `:core:common`, `:core:model`)

#### [MODIFY] [app/build.gradle.kts](file:///Volumes/Gorby/AndroidStudioProjects/DeviceApp/app/build.gradle.kts)
- Configure dynamic `versionCode` and `versionName` generation based on Git commit count, commit hash, and build timestamp.
- Enable `buildFeatures { buildConfig = true }` and expose custom `BuildConfig` fields (`GIT_HASH`, `BUILD_TIME`).

#### [NEW] [AppVersionInfo.kt](file:///Volumes/Gorby/AndroidStudioProjects/DeviceApp/core/common/src/main/java/com/enterprise/busvalidator/core/common/AppVersionInfo.kt)
- Create data model and helper `AppVersionProvider` to dynamically fetch runtime version info (`versionName`, `versionCode`, `gitHash`, `buildType`).

#### [MODIFY] [DomainModels.kt](file:///Volumes/Gorby/AndroidStudioProjects/DeviceApp/core/model/src/main/java/com/enterprise/busvalidator/core/model/DomainModels.kt)
- Add `OperatorBrand` (`BISKITA`, `CITRA`, `SURABAYA`), `OperatorSubService` enum, `FareRulePolicy` data model, and `OperatorConfig` data model containing supported hardware models, branding colors, base URLs, route details, and fare rules.
- Update `TerminalConfig` to hold the active operator configuration.

---

### Core Security & Core Network & Core Payment (`:core:security`, `:core:network`, `:core:payment`, `:core:devicemanager`)

#### [MODIFY] [NativeSecurityVault.kt](file:///Volumes/Gorby/AndroidStudioProjects/DeviceApp/core/security/src/main/java/com/enterprise/busvalidator/core/security/NativeSecurityVault.kt)
- Support returning dynamic Base URLs per operator and sub-service with obfuscation safeguards.

#### [MODIFY] [ApiHttpClient.kt](file:///Volumes/Gorby/AndroidStudioProjects/DeviceApp/core/network/src/main/java/com/enterprise/busvalidator/core/network/ApiHttpClient.kt)
- Retrieve operator-specific Base URL when executing network requests.

#### [MODIFY] [PaymentEngine.kt](file:///Volumes/Gorby/AndroidStudioProjects/DeviceApp/core/payment/src/main/java/com/enterprise/busvalidator/core/payment/PaymentEngine.kt)
- Integrate operator-specific `FareRulePolicy` into `calculateDynamicFare()` so each operator (Biskita, Citra, Surabaya) calculates fares according to its unique rules and passenger profiles.

#### [MODIFY] [DeviceManagementComponents.kt](file:///Volumes/Gorby/AndroidStudioProjects/DeviceApp/core/devicemanager/src/main/java/com/enterprise/busvalidator/core/devicemanager/DeviceManagementComponents.kt)
- Update `InitializationPipelineManager` to load configured operator parameters, Base URL, and validate hardware model compatibility against operator rules.

---

### Feature Modules (`:feature:validator`, `:feature:settings`, `:app`)

#### [MODIFY] [InitializationSplashScreen.kt](file:///Volumes/Gorby/AndroidStudioProjects/DeviceApp/feature/validator/src/main/java/com/enterprise/busvalidator/feature/validator/InitializationSplashScreen.kt)
- Replace static version text (`v2.4.1`) with dynamic version info provided by `AppVersionProvider`.

#### [MODIFY] [ValidatorMainScreen.kt](file:///Volumes/Gorby/AndroidStudioProjects/DeviceApp/feature/validator/src/main/java/com/enterprise/busvalidator/feature/validator/ValidatorMainScreen.kt)
- Implement dynamic UI layout switching:
  - `BiskitaDashboardContent`: Standard Cyan transit style with fare rule badges.
  - `CitraDashboardContent`: Township Shuttle Emerald & Amber style with residential estate zone indicators and Citra fare table.
  - `SurabayaDashboardContent`: Surabaya Municipal Red & Gold style with Wara Wiri / Suroboyo Bus branding, QRIS payment indicators, and municipal fare policy display.

#### [MODIFY] [SettingsScreen.kt](file:///Volumes/Gorby/AndroidStudioProjects/DeviceApp/feature/settings/src/main/java/com/enterprise/busvalidator/feature/settings/SettingsScreen.kt)
- Add interactive Operator & Sub-Service selection UI.
- Display dynamic app version details, active Base URL, Fare Rules summary, and hardware compatibility status.

#### [MODIFY] [MainActivity.kt](file:///Volumes/Gorby/AndroidStudioProjects/DeviceApp/app/src/main/java/com/enterprise/busvalidator/MainActivity.kt)
- Support switching operator profiles dynamically from Settings and passing active operator configs down to UI screens and payment engine.

---

## Verification Plan

### Automated Verification
- Run `./gradlew test` and `./gradlew assembleDebug` via terminal to verify clean compilation.

### Manual Verification
- Launch the application and observe:
  1. Splash screen displays dynamic version name & git commit hash (not static `v2.4.1`).
  2. Switching operators between Biskita (Bekasi/Depok/Bogor), Citra (Citra Raya/Citra Maja), and Surabaya (Wara Wiri/Bus Surabaya) correctly updates UI layouts, active Base URLs, Fare Rules calculations on tap, branding, and supported device checks.
