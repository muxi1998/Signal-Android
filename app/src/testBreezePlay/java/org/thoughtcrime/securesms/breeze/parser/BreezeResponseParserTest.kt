package org.thoughtcrime.securesms.breeze.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.thoughtcrime.securesms.breeze.model.ResponseType

class BreezeResponseParserTest {

    @Test
    fun `parse response type`() {
        val json = """{"type": "response", "text": "Hello world"}"""
        val result = BreezeResponseParser.parse(json)
        assertNotNull(result)
        assertEquals(ResponseType.RESPONSE, result?.type)
        assertEquals("Hello world", result?.text)
    }

    @Test
    fun `parse draft type`() {
        val json = """{"type": "draft", "draft_message": "Hi Alice", "recipient": "Alice", "confirmation_prompt": "Send?"}"""
        val result = BreezeResponseParser.parse(json)
        assertNotNull(result)
        assertEquals(ResponseType.DRAFT, result?.type)
        assertEquals("Hi Alice", result?.draftMessage)
        assertEquals("Alice", result?.recipient)
        assertEquals("Send?", result?.confirmationPrompt)
    }

    @Test
    fun `draft has all fields`() {
        val json = """{
            "type": "draft",
            "draft_message": "Meeting at 3",
            "recipient": "Bob",
            "confirmation_prompt": "Should I send this?"
        }"""
        val result = BreezeResponseParser.parse(json)
        assertNotNull(result)
        assertEquals("Meeting at 3", result?.draftMessage)
        assertEquals("Bob", result?.recipient)
        assertEquals("Should I send this?", result?.confirmationPrompt)
    }

    @Test
    fun `malformed json returns null`() {
        val json = """{"type": "response", "text": "Hello world""" // Missing closing brace
        val result = BreezeResponseParser.parse(json)
        assertNull(result)
    }

    @Test
    fun `missing type defaults to response`() {
        val json = """{"text": "Implicit response"}"""
        val result = BreezeResponseParser.parse(json)
        assertNotNull(result)
        assertEquals(ResponseType.RESPONSE, result?.type)
        assertEquals("Implicit response", result?.text)
    }

    @Test
    fun `missing text in response returns empty string`() {
        val json = """{"type": "response"}"""
        val result = BreezeResponseParser.parse(json)
        assertNotNull(result)
        assertEquals("", result?.text)
    }

    @Test
    fun `draft missing recipient returns null field`() {
        val json = """{"type": "draft", "draft_message": "Hi"}"""
        val result = BreezeResponseParser.parse(json)
        assertNotNull(result)
        assertEquals(ResponseType.DRAFT, result?.type)
        assertEquals("", result?.recipient) // optString returns "" by default
    }

    @Test
    fun `extra fields ignored`() {
        val json = """{"type": "response", "text": "Hello", "extra": "data"}"""
        val result = BreezeResponseParser.parse(json)
        assertNotNull(result)
        assertEquals("Hello", result?.text)
    }

    @Test
    fun `unicode handled`() {
        val json = """{"type": "response", "text": "你好世界"}"""
        val result = BreezeResponseParser.parse(json)
        assertNotNull(result)
        assertEquals("你好世界", result?.text)
    }

    @Test
    fun `newlines escaped`() {
        val json = """{"type": "response", "text": "Line 1\nLine 2"}"""
        val result = BreezeResponseParser.parse(json)
        assertNotNull(result)
        assertEquals("Line 1\nLine 2", result?.text)
    }
}
