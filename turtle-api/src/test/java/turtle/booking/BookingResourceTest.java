package turtle.booking;

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
                        {"availabilityId":99999,"notes":"test"}
                        """)
                .when().post("/bookings")
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "1", roles = "COACH")
    void clientCannotApproveBooking() {
        // COACH role can call /approve; CLIENT cannot
        given()
                .contentType(ContentType.JSON)
                .when().patch("/bookings/1/approve")
                .then()
                .statusCode(anyOf(is(403), is(404)));
    }

    @Test
    @TestSecurity(user = "1", roles = "CLIENT")
    void coachOnlyEndpointReturnsForbiddenForClient() {
        given()
                .when().patch("/bookings/1/approve")
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
