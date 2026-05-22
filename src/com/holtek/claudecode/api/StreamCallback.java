package com.holtek.claudecode.api;

/**
 * Callback interface for streaming responses from the Anthropic API.
 *
 * All methods may be called from a background thread.
 * Use Display.asyncExec() inside implementations for SWT UI updates.
 */
public interface StreamCallback {

    /**
     * Called for each text token received from the SSE stream.
     *
     * @param token  the incremental text fragment (may be a single char or a few words)
     */
    void onToken(String token);

    /**
     * Called once the full response has been received and the stream is closed.
     *
     * @param fullText  the complete concatenated response text
     */
    void onComplete(String fullText);

    /**
     * Called if an error occurs during the API call or stream parsing.
     *
     * @param e  the exception that was thrown
     */
    void onError(Exception e);
}
