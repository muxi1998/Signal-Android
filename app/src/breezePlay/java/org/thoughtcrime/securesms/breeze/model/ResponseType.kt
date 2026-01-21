package org.thoughtcrime.securesms.breeze.model

/**
 * Categorizes the type of response received from the Breeze AI.
 */
enum class ResponseType {
    /**
     * A regular informational response (e.g., translation, summary, answer).
     * No further action is required from the app other than displaying the text.
     */
    RESPONSE,

    /**
     * A message draft composed by the AI that requires user confirmation.
     * The app should show the draft and ask the user if they want to send it.
     */
    DRAFT
}
