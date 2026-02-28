package turtle.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.util.List;

public class ExceptionMappers {

    private static final Logger LOG = Logger.getLogger(ExceptionMappers.class);

    @ServerExceptionMapper
    public Response handleConstraintViolation(ConstraintViolationException e) {
        List<String> errors = e.getConstraintViolations().stream()
                .map(v -> {
                    String path = v.getPropertyPath().toString();
                    int lastDot = path.lastIndexOf('.');
                    String field = lastDot >= 0 ? path.substring(lastDot + 1) : path;
                    return field + ": " + v.getMessage();
                })
                .sorted()
                .toList();
        return Response.status(400)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse("Validation failed", errors))
                .build();
    }

    @ServerExceptionMapper
    public Response handleWebApplicationException(WebApplicationException e) {
        int status = e.getResponse().getStatus();
        String message = e.getMessage() != null ? e.getMessage() : "Request failed";
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(message))
                .build();
    }

    @ServerExceptionMapper
    public Response handleJsonProcessingException(JsonProcessingException e) {
        return Response.status(400)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse("Malformed request body"))
                .build();
    }

    @ServerExceptionMapper
    public Response handleUnhandled(Exception e) {
        LOG.error("Unhandled exception", e);
        return Response.status(500)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse("Internal server error"))
                .build();
    }
}
