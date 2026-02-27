package turtle.notification;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "evolution-api")
@Path("/message")
public interface EvolutionApiClient {

    @POST
    @Path("/sendText/{instance}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    Response sendText(
            @PathParam("instance") String instance,
            @HeaderParam("apikey") String apiKey,
            SendTextRequest body
    );
}
