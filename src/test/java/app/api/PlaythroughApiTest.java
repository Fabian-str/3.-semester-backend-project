package app.api;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;

public class PlaythroughApiTest {

    private static String userToken;

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = 7071;

        String body = """
        {
          "username": "testuser",
          "password": "password123"
        }
        """;

        userToken = given()
            .contentType("application/json")
            .body(body)
            .when()
            .post("/api/auth/login")
            .then()
            .statusCode(200)
            .extract()
            .path("token");
    }

    @Test
    void getAllPlaythroughsShouldReturn200() {
        given()
            .header("Authorization", "Bearer " + userToken)
            .when()
            .get("/api/playthroughs")
            .then()
            .statusCode(200)
            .body("$", isA(List.class));
    }

    @Test
    void getPlaythroughByIdShouldReturn404WhenNotFound() {
        given()
            .header("Authorization", "Bearer " + userToken)
            .when()
            .get("/api/playthroughs/999999")
            .then()
            .statusCode(404)
            .body("error", is("Playthrough not found"));
    }

    @Test
    void createPlaythroughShouldReturn201() {
        String requestBody = """
            {
              "name": "Rest Assured Test Run",
              "game": "RED"
            }
            """;

        given()
            .header("Authorization", "Bearer " + userToken)
            .contentType("application/json")
            .body(requestBody)
            .when()
            .post("/api/playthroughs")
            .then()
            .statusCode(201)
            .body("name", is("Rest Assured Test Run"))
            .body("game", is("RED"));
    }

    @Test
    void updatePlaythroughShouldReturn200() {
        String createBody = """
            {
              "name": "Before Update",
              "game": "RED"
            }
            """;

        int id =
            given()
                .header("Authorization", "Bearer " + userToken)
                .contentType("application/json")
                .body(createBody)
                .when()
                .post("/api/playthroughs")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        String updateBody = """
            {
              "name": "After Update",
              "game": "BLUE"
            }
            """;

        given()
            .header("Authorization", "Bearer " + userToken)
            .contentType("application/json")
            .body(updateBody)
            .when()
            .put("/api/playthroughs/" + id)
            .then()
            .statusCode(200)
            .body("name", is("After Update"))
            .body("game", is("BLUE"));
    }

    @Test
    void deletePlaythroughShouldReturn204() {
        String createBody = """
            {
              "name": "Delete Test Run",
              "game": "RED"
            }
            """;

        int id =
            given()
                .header("Authorization", "Bearer " + userToken)
                .contentType("application/json")
                .body(createBody)
                .when()
                .post("/api/playthroughs")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        given()
            .header("Authorization", "Bearer " + userToken)
            .when()
            .delete("/api/playthroughs/" + id)
            .then()
            .statusCode(204);

        given()
            .header("Authorization", "Bearer " + userToken)
            .when()
            .get("/api/playthroughs/" + id)
            .then()
            .statusCode(404)
            .body("error", is("Playthrough not found"));
    }

    private String loginAndGetToken(String username, String password) {
        String body = """
            {
              "username": "%s",
              "password": "%s"
            }
            """.formatted(username, password);

        return given()
            .header("Authorization", "Bearer " + userToken)
            .contentType("application/json")
            .body(body)
            .when()
            .post("/api/auth/login")
            .then()
            .statusCode(200)
            .extract()
            .path("token");
    }

    @Test
    void userShouldNotAccessAnotherUsersPlaythrough() {

        String userToken = loginAndGetToken("testuser", "password123"
        );

        String adminToken = loginAndGetToken("testadmin", "admin123");

        String body = """
            {
              "name": "Admin Playthrough",
              "game": "RED"
            }
            """;

        int playthroughId = given()
            .header("Authorization", "Bearer " + adminToken)
            .contentType("application/json")
            .body(body)
            .when()
            .post("/api/playthroughs")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        given()
            .header("Authorization", "Bearer " + userToken)
            .when()
            .get("/api/playthroughs/" + playthroughId)
            .then()
            .statusCode(404);
    }
}