package org.thoughtcrime.securesms.mrutil

import org.junit.Assert.assertTrue
import org.junit.Test
import org.thoughtcrime.securesms.mrutil.model.MessageContext
import org.thoughtcrime.securesms.recipients.RecipientId

class MrUtilTransformTest {
    
    @Test
    fun modify_basicMessage_createsValidJson() {
        val context = MessageContext(1L, "user123", RecipientId.from(1L), false)
        
        val result = MrUtil.modify("Hello", context)
        
        // Should contain the basic JSON structure
        assertTrue(result.contains("\"dialog_history\""))
        assertTrue(result.contains("\"text\":\"Hello\""))
    }
}