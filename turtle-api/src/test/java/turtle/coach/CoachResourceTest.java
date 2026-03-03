package turtle.coach;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class CoachResourceTest {

    @Test
    void listCoachesIsPublic() {
        given()
                .when().get("/coaches")
                .then()
                .statusCode(200)
                .body("$", instanceOf(java.util.List.class));
    }

    @Test
    void addTimeWindowRequiresCoachRole() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"startDate":"2030-06-01","endDate":"2030-06-07",
                         "dailyStartTime":"09:00:00","dailyEndTime":"17:00:00",
                         "unitOfWorkMinutes":60,"priority":0}
                        """)
                .when().post("/coaches/1/time-windows")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "1", roles = "COACH")
    void addTimeWindowForbiddenWhenCoachIdDoesNotMatchToken() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"startDate":"2030-06-01","endDate":"2030-06-07",
                         "dailyStartTime":"09:00:00","dailyEndTime":"17:00:00",
                         "unitOfWorkMinutes":60,"priority":0}
                        """)
                .when().post("/coaches/999/time-windows")
                .then()
                .statusCode(403);
    }

    @Test
    void getSlotsRequiresDateParam() {
        given()
                .when().get("/coaches/1/slots")
                .then()
                .statusCode(400);
    }
}
