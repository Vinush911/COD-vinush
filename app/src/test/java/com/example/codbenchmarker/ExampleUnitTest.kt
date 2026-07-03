package com.example.codbenchmarker

import org.junit.Test
import org.junit.Assert.*

/**
 * ExampleUnitTest is a standard JUnit 4 local unit test.
 * Local unit tests run on the local computer's Java Virtual Machine (JVM) and do not 
 * have access to the Android system APIs or hardware devices.
 * Use these tests for testing pure Java/Kotlin algorithmic logic, utility functions, 
 * and state management classes that don't depend on the Android OS.
 *
 * See [Android Testing Documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {

    /**
     * A simple validation test to verify that the local unit testing framework is running correctly.
     * It asserts that basic mathematical addition holds true.
     */
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }
}