package app.api;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

public class AuthApiTest {

    @BeforeAll
    static void setup() {
        ApiTestUsers.setup();

        RestAssured.baseURI = "http://localhost";
        RestAssured.port = 7071;
    }

    @Test
    void protectedRouteWithoutTokenShouldReturn401() {
        given()
                .when()
                .get("/api/playthroughs")
                .then()
                .statusCode(401);
    }

    @Test
    void loginShouldReturn200AndToken() {
        given()
                .contentType("application/json")
                .body(Map.of(
                        "username", ApiTestUsers.USERNAME,
                        "password", ApiTestUsers.PASSWORD
                ))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .body("token", notNullValue());
    }

    @Test
    void protectedRouteWithTokenShouldReturn200() {
        String token = loginAndGetToken(ApiTestUsers.USERNAME);

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/playthroughs")
                .then()
                .statusCode(200);
    }

    @Test
    void userShouldNotAccessAdminRoute() {
        String token = loginAndGetToken(ApiTestUsers.USERNAME);

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/admin/test")
                .then()
                .statusCode(403);
    }

    @Test
    void adminShouldAccessAdminRoute() {
        String token = loginAndGetToken(ApiTestUsers.ADMIN_USERNAME);

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/admin/test")
                .then()
                .statusCode(200);
    }

    @Test
    void registerShouldReturn201() {
        String username = "register_" + UUID.randomUUID();

        given()
                .contentType("application/json")
                .body(Map.of(
                        "username", username,
                        "password", ApiTestUsers.PASSWORD
                ))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(201)
                .body("username", equalTo(username))
                .body("role", equalTo("USER"));
    }

    @Test
    void duplicateUsernameShouldReturn409() {
        given()
                .contentType("application/json")
                .body(Map.of(
                        "username", ApiTestUsers.USERNAME,
                        "password", ApiTestUsers.PASSWORD
                ))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(409);
    }

    private String loginAndGetToken(String username) {
        return given()
                .contentType("application/json")
                .body(Map.of(
                        "username", username,
                        "password", ApiTestUsers.PASSWORD
                ))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("token");
    }
}