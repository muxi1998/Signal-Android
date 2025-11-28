package com.mtkresearch.breeze.edgeai.models

/**
 * Custom exceptions for EdgeAI operations
 */
sealed class EdgeAIException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Service connection failure
 */
class EdgeAIConnectionException(message: String, cause: Throwable? = null) : EdgeAIException(message, cause)

/**
 * Invalid input parameters
 */
class EdgeAIInvalidInputException(message: String, cause: Throwable? = null) : EdgeAIException(message, cause)

/**
 * Model not found or not available
 */
class EdgeAIModelNotFoundException(message: String, cause: Throwable? = null) : EdgeAIException(message, cause)

/**
 * Internal processing error
 */
class EdgeAIInternalException(message: String, cause: Throwable? = null) : EdgeAIException(message, cause)
