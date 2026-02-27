package turtle.notification;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class NotificationService {

    @Inject
    @RestClient
    EvolutionApiClient client;

    @ConfigProperty(name = "evolution.api.key")
    String apiKey;

    @ConfigProperty(name = "evolution.api.instance")
    String instance;

    public void send(String phone, String message) {
        if (phone == null || phone.isBlank()) {
            Log.warnf("Skipping WhatsApp notification: phone number is null or blank");
            return;
        }
        try {
            client.sendText(instance, apiKey, new SendTextRequest(phone, message));
        } catch (Exception e) {
            Log.warnf("WhatsApp notification failed for %s: %s", phone, e.getMessage());
        }
    }
}
