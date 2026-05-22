package com.holtek.claudecode.api;

/**
 * Represents a single message in a Claude conversation.
 *
 * role    : "user" or "assistant"
 * content : the text content of the message
 */
public class ChatMessage {

    private final String role;
    private final String content;

    public ChatMessage(String role, String content) {
        if (role == null || role.isEmpty()) {
            throw new IllegalArgumentException("role must not be empty");
        }
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        this.role    = role;
        this.content = content;
    }

    public String getRole()    { return role; }
    public String getContent() { return content; }

    @Override
    public String toString() {
        String preview = content.length() > 60
                ? content.substring(0, 60) + "..."
                : content;
        return "[" + role + "] " + preview;
    }
}
