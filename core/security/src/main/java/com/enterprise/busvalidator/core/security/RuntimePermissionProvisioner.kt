package com.enterprise.busvalidator.core.security

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class PermissionProvisioningReport(
    val grantedRuntimePermissions: List<String>,
    val alreadyGrantedRuntimePermissions: List<String>,
    val missingManifestPermissions: List<String>,
    val failedRuntimePermissions: List<String>,
    val successfulSystemCommands: List<String>,
    val failedSystemCommands: List<String>
) {
    val isFullyProvisioned: Boolean
        get() = missingManifestPermissions.isEmpty() &&
            failedRuntimePermissions.isEmpty() &&
            failedSystemCommands.isEmpty()
}

/**
 * Root-backed permission bootstrap for dedicated validator devices.
 *
 * The validator is deployed as unattended hardware, so runtime permission prompts are
 * treated as a provisioning failure and resolved with root package-manager commands.
 */
@Singleton
class RuntimePermissionProvisioner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val suManager: SuManager,
    private val logger: EncryptedLogger
) {
    private val provisionMutex = Mutex()

    @Volatile
    private var cachedReport: PermissionProvisioningReport? = null

    suspend fun ensureProvisioned(): PermissionProvisioningReport = provisionMutex.withLock {
        cachedReport?.takeIf { it.isFullyProvisioned }?.let { return@withLock it }

        withContext(Dispatchers.IO) {
            val report = provisionPermissions()
            if (report.isFullyProvisioned) {
                cachedReport = report
            }
            report
        }
    }

    fun requiredRuntimePermissions(): List<String> {
        return runtimePermissions
            .filter { it.isSupportedOnCurrentSdk() }
            .map { it.permission }
    }

    private fun provisionPermissions(): PermissionProvisioningReport {
        val packageName = context.packageName
        val declaredPermissions = readDeclaredPermissions(packageName)
        val devicePolicyManager = getDevicePolicyManager()
        val deviceAdminComponent = ComponentName(packageName, "$packageName.app.BusValidatorDeviceAdminReceiver")

        val grantedRuntimePermissions = mutableListOf<String>()
        val alreadyGrantedRuntimePermissions = mutableListOf<String>()
        val missingManifestPermissions = mutableListOf<String>()
        val failedRuntimePermissions = mutableListOf<String>()

        runtimePermissions
            .filter { it.isSupportedOnCurrentSdk() }
            .forEach { requiredPermission ->
                val permission = requiredPermission.permission
                if (permission !in declaredPermissions) {
                    missingManifestPermissions += permission
                    logger.log(
                        "PermissionProvisioner",
                        "Missing manifest declaration for $permission (${requiredPermission.reason})",
                        isError = true
                    )
                    return@forEach
                }

                if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                    alreadyGrantedRuntimePermissions += permission
                    return@forEach
                }

                val granted = suManager.executeRootCommand(
                    "pm grant ${shellQuote(packageName)} ${shellQuote(permission)}"
                ) || grantPermissionWithDeviceOwner(
                    devicePolicyManager = devicePolicyManager,
                    adminComponent = deviceAdminComponent,
                    packageName = packageName,
                    permission = permission
                )

                if (
                    granted ||
                    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                ) {
                    grantedRuntimePermissions += permission
                } else {
                    failedRuntimePermissions += permission
                }
            }

        val successfulSystemCommands = mutableListOf<String>()
        val failedSystemCommands = mutableListOf<String>()

        rootCommands(packageName)
            .filter { it.isSupportedOnCurrentSdk() }
            .forEach { command ->
                val success = suManager.executeRootCommand(command.command) || command.fallback?.invoke() == true
                if (success) {
                    successfulSystemCommands += command.description
                } else {
                    failedSystemCommands += command.description
                    logger.log(
                        "PermissionProvisioner",
                        "System provisioning command failed: ${command.description}",
                        isError = true
                    )
                }
            }

        return PermissionProvisioningReport(
            grantedRuntimePermissions = grantedRuntimePermissions,
            alreadyGrantedRuntimePermissions = alreadyGrantedRuntimePermissions,
            missingManifestPermissions = missingManifestPermissions,
            failedRuntimePermissions = failedRuntimePermissions,
            successfulSystemCommands = successfulSystemCommands,
            failedSystemCommands = failedSystemCommands
        ).also { report ->
            logger.log(
                "PermissionProvisioner",
                "Provisioned permissions. granted=${report.grantedRuntimePermissions.size}, " +
                    "alreadyGranted=${report.alreadyGrantedRuntimePermissions.size}, " +
                    "missingManifest=${report.missingManifestPermissions.size}, " +
                    "failedRuntime=${report.failedRuntimePermissions.size}, " +
                    "systemOk=${report.successfulSystemCommands.size}, " +
                    "systemFailed=${report.failedSystemCommands.size}"
            )
        }
    }

    private fun grantPermissionWithDeviceOwner(
        devicePolicyManager: DevicePolicyManager?,
        adminComponent: ComponentName,
        packageName: String,
        permission: String
    ): Boolean {
        if (devicePolicyManager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false
        }

        if (!devicePolicyManager.isDeviceOwnerApp(packageName) && !devicePolicyManager.isProfileOwnerApp(packageName)) {
            logger.log(
                "PermissionProvisioner",
                "Root permission grant failed and Device Owner fallback is not active for $permission",
                isError = true
            )
            return false
        }

        return try {
            val state = devicePolicyManager.setPermissionGrantState(
                adminComponent,
                packageName,
                permission,
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
            )
            logger.log("PermissionProvisioner", "Device Owner permission fallback for $permission: $state")
            state
        } catch (e: Exception) {
            logger.log(
                "PermissionProvisioner",
                "Device Owner permission fallback failed for $permission: ${e.message}",
                isError = true
            )
            false
        }
    }

    private fun setHomeWithDeviceOwner(
        devicePolicyManager: DevicePolicyManager?,
        adminComponent: ComponentName,
        packageName: String
    ): Boolean {
        if (devicePolicyManager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false
        }

        if (!devicePolicyManager.isDeviceOwnerApp(packageName)) {
            logger.log(
                "PermissionProvisioner",
                "Root HOME setup failed and Device Owner fallback is not active",
                isError = true
            )
            return false
        }

        return try {
            val filter = android.content.IntentFilter(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_HOME)
                addCategory(android.content.Intent.CATEGORY_DEFAULT)
            }
            val activity = ComponentName(packageName, "$packageName.MainActivity")
            devicePolicyManager.addPersistentPreferredActivity(adminComponent, filter, activity)
            logger.log("PermissionProvisioner", "Device Owner HOME fallback configured")
            true
        } catch (e: Exception) {
            logger.log(
                "PermissionProvisioner",
                "Device Owner HOME fallback failed: ${e.message}",
                isError = true
            )
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun readDeclaredPermissions(packageName: String): Set<String> {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                )
            } else {
                context.packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            }

            packageInfo.requestedPermissions?.toSet().orEmpty()
        } catch (e: Exception) {
            logger.log(
                "PermissionProvisioner",
                "Failed to read manifest permissions: ${e.message}",
                isError = true
            )
            emptySet()
        }
    }

    private fun rootCommands(packageName: String): List<RootProvisionCommand> {
        val devicePolicyManager = getDevicePolicyManager()
        val deviceAdminComponent = ComponentName(packageName, "$packageName.app.BusValidatorDeviceAdminReceiver")
        val packageArg = shellQuote(packageName)
        val activityArg = shellQuote("$packageName/.MainActivity")
        val bootReceiverArg = shellQuote("$packageName/.app.BootReceiver")

        return listOf(
            RootProvisionCommand(
                description = "enable GPS provider",
                command = "cmd location set-location-enabled true",
                minSdk = Build.VERSION_CODES.P
            ),
            RootProvisionCommand(
                description = "enable legacy GPS location mode",
                command = "settings put secure location_mode 3",
                maxSdk = Build.VERSION_CODES.O_MR1
            ),
            RootProvisionCommand(
                description = "allow fine location app-op",
                command = "appops set $packageArg ACCESS_FINE_LOCATION allow",
                minSdk = Build.VERSION_CODES.M
            ),
            RootProvisionCommand(
                description = "allow coarse location app-op",
                command = "appops set $packageArg ACCESS_COARSE_LOCATION allow",
                minSdk = Build.VERSION_CODES.M
            ),
            RootProvisionCommand(
                description = "allow background location app-op",
                command = "appops set $packageArg ACCESS_BACKGROUND_LOCATION allow",
                minSdk = Build.VERSION_CODES.Q
            ),
            RootProvisionCommand(
                description = "allow camera app-op",
                command = "appops set $packageArg CAMERA allow",
                minSdk = Build.VERSION_CODES.M
            ),
            RootProvisionCommand(
                description = "allow background execution app-op",
                command = "appops set $packageArg RUN_IN_BACKGROUND allow",
                minSdk = Build.VERSION_CODES.M
            ),
            RootProvisionCommand(
                description = "allow unrestricted background execution app-op",
                command = "appops set $packageArg RUN_ANY_IN_BACKGROUND allow",
                minSdk = Build.VERSION_CODES.P
            ),
            RootProvisionCommand(
                description = "allow wake lock app-op",
                command = "appops set $packageArg WAKE_LOCK allow",
                minSdk = Build.VERSION_CODES.M
            ),
            RootProvisionCommand(
                description = "disable battery idle restrictions",
                command = "dumpsys deviceidle whitelist +$packageArg",
                minSdk = Build.VERSION_CODES.M
            ),
            RootProvisionCommand(
                description = "enable boot receiver",
                command = "pm enable $bootReceiverArg"
            ),
            RootProvisionCommand(
                description = "set validator as default home",
                command = "cmd package set-home-activity $activityArg",
                minSdk = Build.VERSION_CODES.M,
                fallback = {
                    setHomeWithDeviceOwner(
                        devicePolicyManager = devicePolicyManager,
                        adminComponent = deviceAdminComponent,
                        packageName = packageName
                    )
                }
            )
        )
    }

    private fun shellQuote(value: String): String {
        return "'${value.replace("'", "'\\''")}'"
    }

    private fun getDevicePolicyManager(): DevicePolicyManager? {
        return context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
    }

    private data class RuntimePermission(
        val permission: String,
        val reason: String,
        val minSdk: Int = Build.VERSION_CODES.BASE,
        val maxSdk: Int = Int.MAX_VALUE
    ) {
        fun isSupportedOnCurrentSdk(): Boolean {
            return Build.VERSION.SDK_INT in minSdk..maxSdk
        }
    }

    private data class RootProvisionCommand(
        val description: String,
        val command: String,
        val minSdk: Int = Build.VERSION_CODES.BASE,
        val maxSdk: Int = Int.MAX_VALUE,
        val fallback: (() -> Boolean)? = null
    ) {
        fun isSupportedOnCurrentSdk(): Boolean {
            return Build.VERSION.SDK_INT in minSdk..maxSdk
        }
    }

    private companion object {
        val runtimePermissions = listOf(
            RuntimePermission(
                permission = Manifest.permission.ACCESS_FINE_LOCATION,
                reason = "GPS route telemetry and NMEA time source"
            ),
            RuntimePermission(
                permission = Manifest.permission.ACCESS_COARSE_LOCATION,
                reason = "fallback location provider"
            ),
            RuntimePermission(
                permission = Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                reason = "unattended route telemetry after boot/background",
                minSdk = Build.VERSION_CODES.Q
            ),
            RuntimePermission(
                permission = Manifest.permission.CAMERA,
                reason = "E60V2 CameraX QRIS scanner"
            ),
            RuntimePermission(
                permission = Manifest.permission.READ_EXTERNAL_STORAGE,
                reason = "legacy OTA package and encrypted backup import",
                maxSdk = Build.VERSION_CODES.S_V2
            ),
            RuntimePermission(
                permission = Manifest.permission.WRITE_EXTERNAL_STORAGE,
                reason = "legacy OTA package and encrypted backup export",
                maxSdk = Build.VERSION_CODES.P
            )
        )
    }
}
