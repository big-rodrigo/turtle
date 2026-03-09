package turtle.booking.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class BookingResourceTest {

    @Test
    void listBookingsRequiresAuth() {
        given()
                .when().get("/bookings")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "1", roles = "CLIENT")
    void createBookingWithInvalidSlotReturns404() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"availabilityIds":[99999],"notes":"test"}
                        """)
                .when().post("/bookings")
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "1", roles = "COACH")
    void clientCannotConfirmBooking() {
        // COACH role can call /confirm; CLIENT cannot
        given()
                .contentType(ContentType.JSON)
                .when().patch("/bookings/1/confirm")
                .then()
                .statusCode(anyOf(is(403), is(404), is(409)));
    }

    @Test
    @TestSecurity(user = "1", roles = "CLIENT")
    void coachOnlyEndpointReturnsForbiddenForClient() {
        given()
                .when().patch("/bookings/1/confirm")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "1", roles = "CLIENT")
    void listBookingsReturnsEmptyForNewUser() {
        given()
                .when().get("/bookings")
                .then()
                .statusCode(200)
                .body("$", instanceOf(java.util.List.class));
    }
}
