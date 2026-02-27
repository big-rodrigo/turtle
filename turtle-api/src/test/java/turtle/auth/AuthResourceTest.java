package turtle.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class AuthResourceTest {

    @Test
    void registerClientReturns201WithToken() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name":"Alice","email":"alice@test.com","phone":"5511900000001",
                         "password":"secret","role":"CLIENT"}
                        """)
                .when().post("/auth/register")
                .then()
                .statusCode(201)
                .body("token", notNullValue());
    }

    @Test
    void registerCoachReturns201WithToken() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name":"Bob","email":"bob@test.com","phone":"5511900000002",
                         "password":"secret","role":"COACH"}
                        """)
                .when().post("/auth/register")
                .then()
                .statusCode(201)
                .body("token", notNullValue());
    }

    @Test
    void duplicateEmailReturns409() {
        String body = """
                {"name":"Carol","email":"carol@test.com","phone":"5511900000003",
                 "password":"secret","role":"CLIENT"}
                """;
        given().contentType(ContentType.JSON).body(body).post("/auth/register");

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/auth/register")
                .then()
                .statusCode(409);
    }

    @Test
    void loginWithValidCredentialsReturnsToken() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name":"Dave","email":"dave@test.com","phone":"5511900000004",
                         "password":"secret","role":"CLIENT"}
                        """)
                .post("/auth/register");

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"email":"dave@test.com","password":"secret"}
                        """)
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("token", notNullValue());
    }

    @Test
    void loginWithWrongPasswordReturns401() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name":"Eve","email":"eve@test.com","phone":"5511900000005",
                         "password":"secret","role":"CLIENT"}
                        """)
                .post("/auth/register");

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"email":"eve@test.com","password":"wrong"}
                        """)
                .when().post("/auth/login")
                .then()
                .statusCode(401);
    }
}
