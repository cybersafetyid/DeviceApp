package com.enterprise.busvalidator.core.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class TimeAnchor(
    val trustedUtcMs: Long,
    val elapsedRealtimeMs: Long,
    val lastKnownGoodUtcMs: Long,
    val source: String,
    val uncertaintyMs: Long
)

interface TimeAnchorStore {
    fun readAnchor(): TimeAnchor?
    fun writeAnchor(anchor: TimeAnchor)
}

@Singleton
class SharedPreferencesTimeAnchorStore @Inject constructor(
    @ApplicationContext context: Context
) : TimeAnchorStore {
    private val preferences = context.getSharedPreferences("time_anchor_store", Context.MODE_PRIVATE)

    override fun readAnchor(): TimeAnchor? {
        val trustedUtcMs = preferences.getLong(KEY_TRUSTED_UTC, 0L)
        val elapsedRealtimeMs = preferences.getLong(KEY_ELAPSED_REALTIME, -1L)
        val lastKnownGoodUtcMs = preferences.getLong(KEY_LAST_KNOWN_GOOD_UTC, 0L)
        val source = preferences.getString(KEY_SOURCE, null) ?: return null
        val uncertaintyMs = preferences.getLong(KEY_UNCERTAINTY, Long.MAX_VALUE)
        val storedDigest = preferences.getString(KEY_DIGEST, null) ?: return null

        if (trustedUtcMs <= 0L || elapsedRealtimeMs < 0L || lastKnownGoodUtcMs <= 0L) {
            return null
        }

        val anchor = TimeAnchor(
            trustedUtcMs = trustedUtcMs,
            elapsedRealtimeMs = elapsedRealtimeMs,
            lastKnownGoodUtcMs = lastKnownGoodUtcMs,
            source = source,
            uncertaintyMs = uncertaintyMs
        )

        return if (storedDigest == digest(anchor)) anchor else null
    }

    override fun writeAnchor(anchor: TimeAnchor) {
        preferences.edit()
            .putLong(KEY_TRUSTED_UTC, anchor.trustedUtcMs)
            .putLong(KEY_ELAPSED_REALTIME, anchor.elapsedRealtimeMs)
            .putLong(KEY_LAST_KNOWN_GOOD_UTC, anchor.lastKnownGoodUtcMs)
            .putString(KEY_SOURCE, anchor.source)
            .putLong(KEY_UNCERTAINTY, anchor.uncertaintyMs)
            .putString(KEY_DIGEST, digest(anchor))
            .apply()
    }

    private fun digest(anchor: TimeAnchor): String {
        val material = listOf(
            anchor.trustedUtcMs,
            anchor.elapsedRealtimeMs,
            anchor.lastKnownGoodUtcMs,
            anchor.source,
            anchor.uncertaintyMs,
            DIGEST_PEPPER
        ).joinToString("|")

        return MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val KEY_TRUSTED_UTC = "trusted_utc_ms"
        const val KEY_ELAPSED_REALTIME = "elapsed_realtime_ms"
        const val KEY_LAST_KNOWN_GOOD_UTC = "last_known_good_utc_ms"
        const val KEY_SOURCE = "source"
        const val KEY_UNCERTAINTY = "uncertainty_ms"
        const val KEY_DIGEST = "digest"
        const val DIGEST_PEPPER = "EnterpriseBusValidatorTimeAnchor2026"
    }
}

class InMemoryTimeAnchorStore(
    initialAnchor: TimeAnchor? = null
) : TimeAnchorStore {
    private var anchor: TimeAnchor? = initialAnchor

    override fun readAnchor(): TimeAnchor? = anchor

    override fun writeAnchor(anchor: TimeAnchor) {
        this.anchor = anchor
    }
}
