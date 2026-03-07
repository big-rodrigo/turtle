package turtle.conversation.api.dto;

import java.time.LocalDateTime;

public record MessageResponse(Long id, Long senderId, String senderName, String content, LocalDateTime sentAt) {}
