package org.thoughtcrime.securesms.mrutil.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TransformedMessageTest {
    
    @Test
    fun constructor_validParameters_createsObject() {
        val history = listOf(DialogMessage("user1", "Hello"))
        val message = TransformedMessage(history, "Hi there")
        
        assertEquals(1, message.dialogHistory.size)
        assertEquals("Hi there", message.text)
    }
    
    @Test
    fun constructor_emptyHistory_createsObject() {
        val message = TransformedMessage(emptyList(), "Hello")
        
        assertTrue(message.dialogHistory.isEmpty())
        assertEquals("Hello", message.text)
    }
    
    @Test
    fun constructor_emptyText_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            TransformedMessage(emptyList(), "")
        }
    }
    
    @Test
    fun toJson_validMessage_returnsJsonString() {
        val history = listOf(DialogMessage("user1", "Hello"))
        val message = TransformedMessage(history, "Hi")
        
        val json = message.toJson()
        
        assertTrue(json.contains("\"dialog_history\""))
        assertTrue(json.contains("\"text\":\"Hi\""))
        assertTrue(json.contains("\"sender\":\"user1\""))
        assertTrue(json.contains("\"text\":\"Hello\""))
    }
    
    @Test
    fun fromJson_validJson_returnsMessage() {
        val json = """{"dialog_history":[{"sender":"user1","text":"Hello","timestamp":0}],"text":"Hi"}"""
        
        val message = TransformedMessage.fromJson(json)
        
        assertEquals("Hi", message.text)
        assertEquals(1, message.dialogHistory.size)
        assertEquals("user1", message.dialogHistory[0].sender)
        assertEquals("Hello", message.dialogHistory[0].text)
    }
    
    @Test
    fun roundTrip_toJsonThenFromJson_preservesData() {
        val history = listOf(
            DialogMessage("user1", "Hello", 1700000000L),
            DialogMessage("user2", "Hi there", 1700000001L)
        )
        val original = TransformedMessage(history, "How are you?")
        
        val json = original.toJson()
        val parsed = TransformedMessage.fromJson(json)
        
        assertEquals(original.text, parsed.text)
        assertEquals(original.dialogHistory.size, parsed.dialogHistory.size)
        assertEquals(original.dialogHistory[0].sender, parsed.dialogHistory[0].sender)
        assertEquals(original.dialogHistory[1].text, parsed.dialogHistory[1].text)
    }
}