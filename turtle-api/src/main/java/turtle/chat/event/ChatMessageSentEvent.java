package turtle.chat.event;

import turtle.chat.ChatMessage;

public record ChatMessageSentEvent(ChatMessage message) {}
