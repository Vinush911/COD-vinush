package com.example.codbenchmarker

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * ExampleInstrumentedTest is an instrumented Android JUnit test.
 * Unlike local unit tests, instrumented tests run on a physical Android device or emulator.
 * They have full access to the Android runtime environment, allowing you to fetch the application 
 * Context, access system databases/shared preferences, load native assets, and perform UI testing.
 *
 * See [Android Testing Documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    /**
     * Verifies that the instrumented runtime environment matches the target application configuration
     * by resolving the target context and checking the package name.
     */
    @Test
    fun useAppContext() {
        // Obtain the Context of the application under test from the Instrumentation Registry
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Assert that the application package matches the expected package name identifier
        assertEquals("com.example.codbenchmarker", appContext.packageName)
    }
}