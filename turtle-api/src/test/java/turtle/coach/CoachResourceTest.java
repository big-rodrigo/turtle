package turtle.coach;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class CoachResourceTest {

    private static final String SLOT_BODY = """
            {"startsAt":"2030-06-01T10:00:00","endsAt":"2030-06-01T11:00:00"}
            """;

    @Test
    void listCoachesIsPublic() {
        given()
                .when().get("/coaches")
                .then()
                .statusCode(200)
                .body("$", instanceOf(java.util.List.class));
    }

    @Test
    @TestSecurity(user = "1", roles = "COACH")
    void addSlotForbiddenWhenCoachIdDoesNotMatchToken() {
        given()
                .contentType(ContentType.JSON)
                .body(SLOT_BODY)
                .when().post("/coaches/999/availability")
                .then()
                .statusCode(403);
    }

    @Test
    void addSlotRequiresCoachRole() {
        given()
                .contentType(ContentType.JSON)
                .body(SLOT_BODY)
                .when().post("/coaches/1/availability")
                .then()
                .statusCode(401);
    }
}
