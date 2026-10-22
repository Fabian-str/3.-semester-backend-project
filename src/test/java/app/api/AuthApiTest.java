package app.api;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

public class AuthApiTest {

    @BeforeAll
    static void setup() {
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
        String body = """
            {
              "username": "testuser",
              "password": "test123"
            }
            """;

        given()
            .contentType("application/json")
            .body(body)
            .when()
            .post("/api/auth/login")
            .then()
            .statusCode(200)
            .body("token", notNullValue());
    }

    @Test
    void protectedRouteWithTokenShouldReturn200() {
        String loginBody = """
            {
              "username": "testuser",
              "password": "test123"
            }
            """;

        String token = given()
            .contentType("application/json")
            .body(loginBody)
            .when()
            .post("/api/auth/login")
            .then()
            .statusCode(200)
            .extract()
            .path("token");

        given()
            .header("Authorization", "Bearer " + token)
            .when()
            .get("/api/playthroughs")
            .then()
            .statusCode(200);
    }

    @Test
    void userShouldNotAccessAdminRoute() {

        String loginBody = """
            {
              "username": "testuser",
              "password": "test123"
            }
            """;

        String token = given()
            .contentType("application/json")
            .body(loginBody)
            .when()
            .post("/api/auth/login")
            .then()
            .statusCode(200)
            .extract()
            .path("token");

        given()
            .header("Authorization", "Bearer " + token)
            .when()
            .get("/api/admin/test")
            .then()
            .statusCode(403);
    }

    @Test
    void adminShouldAccessAdminRoute() {

        String loginBody = """
            {
              "username": "testadmin",
              "password": "test123"
            }
            """;

        String token = given()
            .contentType("application/json")
            .body(loginBody)
            .when()
            .post("/api/auth/login")
            .then()
            .statusCode(200)
            .extract()
            .path("token");

        given()
            .header("Authorization", "Bearer " + token)
            .when()
            .get("/api/admin/test")
            .then()
            .statusCode(200);
    }

    @Test
    void registerShouldReturn201() {

        String body = """
            {
              "username": "newtestuser",
              "password": "password123"
            }
            """;

        given()
            .contentType("application/json")
            .body(body)
            .when()
            .post("/api/auth/register")
            .then()
            .statusCode(201)
            .body("username", equalTo("newtestuser"))
            .body("role", equalTo("USER"));
    }

    @Test
    void duplicateUsernameShouldReturn409() {

        String body = """
            {
              "username": "testuser",
              "password": "password123"
            }
            """;

        given()
            .contentType("application/json")
            .body(body)
            .when()
            .post("/api/auth/register")
            .then()
            .statusCode(409);
    }
}
