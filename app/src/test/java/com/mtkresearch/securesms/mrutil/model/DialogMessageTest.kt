package org.thoughtcrime.securesms.mrutil.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DialogMessageTest {
    
    @Test
    fun constructor_validParameters_createsObject() {
        val message = DialogMessage("user123", "Hello", 1700000000L)
        
        assertEquals("user123", message.sender)
        assertEquals("Hello", message.text)
        assertEquals(1700000000L, message.timestamp)
    }
    
    @Test
    fun constructor_defaultTimestamp_setsZero() {
        val message = DialogMessage("user123", "Hello")
        
        assertEquals(0L, message.timestamp)
    }
    
    @Test
    fun constructor_emptySender_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            DialogMessage("", "Hello")
        }
    }
    
    @Test
    fun constructor_emptyText_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            DialogMessage("user123", "")
        }
    }
    
    @Test
    fun constructor_negativeTimestamp_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            DialogMessage("user123", "Hello", -1L)
        }
    }
}