package org.thoughtcrime.securesms.mrutil

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.thoughtcrime.securesms.mrutil.model.MessageContext
import org.thoughtcrime.securesms.recipients.RecipientId

class MrUtilTest {
    
    @Test
    fun modify_basicMessage_returnsJson() {
        val context = MessageContext(
            threadId = 1L,
            senderId = "user123",
            recipientId = RecipientId.from(1L),
            isGroup = false
        )
        
        val result = MrUtil.modify("Hello", context)
        
        assertTrue(result.contains("\"dialog_history\""))
        assertTrue(result.contains("\"text\":\"Hello\""))
    }
    
    @Test
    fun modify_emptyText_returnsOriginalText() {
        val context = MessageContext(
            threadId = 3L,
            senderId = "user123",
            recipientId = RecipientId.from(3L),
            isGroup = false
        )
        
        val result = MrUtil.modify("", context)
        
        assertEquals("", result)
    }
    
    
    companion object {
        private const val TEST_THREAD_ID = 1L
        private const val TEST_SENDER = "user123"
    }
}