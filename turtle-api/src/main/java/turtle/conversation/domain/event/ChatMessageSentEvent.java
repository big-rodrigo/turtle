package turtle.conversation.domain.event;

import turtle.conversation.domain.ChatMessage;

public record ChatMessageSentEvent(ChatMessage message) {}
