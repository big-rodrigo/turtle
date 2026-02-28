package turtle.chat;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import turtle.chat.dto.MessageResponse;
import turtle.chat.dto.SendMessageRequest;

import java.util.List;

@Path("/bookings/{bookingId}/messages")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class ChatResource {

    @Inject
    ChatService chatService;

    @Inject
    JsonWebToken jwt;

    @GET
    public List<MessageResponse> list(@PathParam("bookingId") Long bookingId) {
        Long userId = Long.parseLong(jwt.getSubject());
        return chatService.listMessages(bookingId, userId).stream()
                .map(m -> new MessageResponse(m.id, m.sender.id, m.sender.name, m.content, m.sentAt))
                .toList();
    }

    @POST
    public Response send(@PathParam("bookingId") Long bookingId, @Valid SendMessageRequest req) {
        Long userId = Long.parseLong(jwt.getSubject());
        ChatMessage msg = chatService.sendMessage(bookingId, userId, req.content());
        return Response.status(201)
                .entity(new MessageResponse(msg.id, msg.sender.id, msg.sender.name, msg.content, msg.sentAt))
                .build();
    }
}
