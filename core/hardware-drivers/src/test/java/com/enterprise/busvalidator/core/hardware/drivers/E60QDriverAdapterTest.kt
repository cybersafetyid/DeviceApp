package com.enterprise.busvalidator.core.hardware.drivers

import com.enterprise.busvalidator.core.hardware.api.SoundType
import com.enterprise.busvalidator.core.security.EncryptedLogger
import io.mockk.*
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class E60QDriverAdapterTest {

    private lateinit var logger: EncryptedLogger
    private lateinit var adapter: E60QDriverAdapter

    @Before
    fun setUp() {
        logger = mockk(relaxed = true)
        adapter = E60QDriverAdapter(logger)
    }

    @Test
    fun `test hardware is available`() {
        assertTrue(adapter.isHardwareAvailable())
    }
}
