package turtle.chat;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import turtle.chat.dto.MessageResponse;
import turtle.chat.dto.SendMessageRequest;

import java.util.List;

@Tag(name = "Chat", description = "In-booking messaging between client and coach")
@SecurityRequirement(name = "bearerAuth")
@Path("/bookings/{bookingId}/messages")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class ChatResource {

    @Inject
    ChatService chatService;

    @Inject
    JsonWebToken jwt;

    @Operation(summary = "List messages for a booking", description = "Returns all chat messages for the given booking. Caller must be a participant (client or coach) of that booking.")
    @APIResponse(responseCode = "200", description = "List of messages",
            content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    @APIResponse(responseCode = "403", description = "Caller is not a participant in this booking")
    @APIResponse(responseCode = "404", description = "Booking not found")
    @GET
    public List<MessageResponse> list(@PathParam("bookingId") Long bookingId) {
        Long userId = Long.parseLong(jwt.getSubject());
        return chatService.listMessages(bookingId, userId).stream()
                .map(m -> new MessageResponse(m.id, m.sender.id, m.sender.name, m.content, m.sentAt))
                .toList();
    }

    @Operation(summary = "Send a message in a booking", description = "Sends a chat message in the context of a booking. Caller must be a participant.")
    @APIResponse(responseCode = "201", description = "Message sent",
            content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    @APIResponse(responseCode = "400", description = "Validation error (empty content)")
    @APIResponse(responseCode = "403", description = "Caller is not a participant in this booking")
    @APIResponse(responseCode = "404", description = "Booking not found")
    @POST
    public Response send(@PathParam("bookingId") Long bookingId, @Valid SendMessageRequest req) {
        Long userId = Long.parseLong(jwt.getSubject());
        ChatMessage msg = chatService.sendMessage(bookingId, userId, req.content());
        return Response.status(201)
                .entity(new MessageResponse(msg.id, msg.sender.id, msg.sender.name, msg.content, msg.sentAt))
                .build();
    }
}
