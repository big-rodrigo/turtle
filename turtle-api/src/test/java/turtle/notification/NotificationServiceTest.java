package turtle.notification;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@QuarkusTest
class NotificationServiceTest {

    @InjectMock
    @RestClient
    EvolutionApiClient evolutionApiClient;

    @Inject
    NotificationService notificationService;

    @Test
    void sendCallsEvolutionApiWithPhoneAndMessage() {
        Mockito.when(evolutionApiClient.sendText(anyString(), anyString(), any()))
                .thenReturn(Response.ok().build());

        notificationService.send("5511999999999", "Hello coach!");

        verify(evolutionApiClient).sendText(
                eq("test"),
                eq("test-key"),
                eq(new SendTextRequest("5511999999999", "Hello coach!")));
    }

    @Test
    void sendSkipsCallWhenPhoneIsNull() {
        notificationService.send(null, "Hello!");
        verify(evolutionApiClient, never()).sendText(any(), any(), any());
    }

    @Test
    void sendSkipsCallWhenPhoneIsBlank() {
        notificationService.send("  ", "Hello!");
        verify(evolutionApiClient, never()).sendText(any(), any(), any());
    }

    @Test
    void sendDoesNotThrowWhenEvolutionApiIsDown() {
        Mockito.when(evolutionApiClient.sendText(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Connection refused"));

        // Must not propagate the exception
        notificationService.send("5511999999999", "Hello!");
    }
}
