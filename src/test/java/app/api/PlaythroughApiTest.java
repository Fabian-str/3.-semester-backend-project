package app.api;

import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;

public class PlaythroughApiTest {

    private static String userToken;
    private static String adminToken;

    private final List<CreatedPlaythrough> createdPlaythroughs =
            new ArrayList<>();

    @BeforeAll
    static void setup() {
        ApiTestUsers.setup();

        RestAssured.baseURI = "http://localhost";
        RestAssured.port = 7071;

        userToken = loginAndGetToken(ApiTestUsers.USERNAME);
        adminToken = loginAndGetToken(ApiTestUsers.ADMIN_USERNAME);
    }

    @AfterEach
    void cleanup() {
        for (CreatedPlaythrough created : createdPlaythroughs) {
            given()
                    .header(
                            "Authorization",
                            "Bearer " + created.token()
                    )
                    .when()
                    .delete("/api/playthroughs/" + created.id())
                    .then()
                    .statusCode(204);
        }
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
                .get("/api/playthroughs/" + Long.MAX_VALUE)
                .then()
                .statusCode(404)
                .body("error", is("Playthrough not found"));
    }

    @Test
    void createPlaythroughShouldReturn201() {
        createPlaythrough(
                userToken,
                "Rest Assured Test Run",
                "RED"
        );
    }

    @Test
    void updatePlaythroughShouldReturn200() {
        long id = createPlaythrough(
                userToken,
                "Before Update",
                "RED"
        );

        given()
                .header("Authorization", "Bearer " + userToken)
                .contentType("application/json")
                .body(Map.of(
                        "name", "After Update",
                        "game", "BLUE"
                ))
                .when()
                .put("/api/playthroughs/" + id)
                .then()
                .statusCode(200)
                .body("name", is("After Update"))
                .body("game", is("BLUE"));
    }

    @Test
    void deletePlaythroughShouldReturn204() {
        long id = createPlaythrough(
                userToken,
                "Delete Test Run",
                "RED"
        );

        given()
                .header("Authorization", "Bearer " + userToken)
                .when()
                .delete("/api/playthroughs/" + id)
                .then()
                .statusCode(204);

        createdPlaythroughs.removeIf(
                created -> created.id() == id
        );

        given()
                .header("Authorization", "Bearer " + userToken)
                .when()
                .get("/api/playthroughs/" + id)
                .then()
                .statusCode(404)
                .body("error", is("Playthrough not found"));
    }

    @Test
    void userShouldNotAccessAnotherUsersPlaythrough() {
        long id = createPlaythrough(
                adminToken,
                "Admin Playthrough",
                "RED"
        );

        given()
                .header("Authorization", "Bearer " + userToken)
                .when()
                .get("/api/playthroughs/" + id)
                .then()
                .statusCode(404);
    }

    private long createPlaythrough(
            String token,
            String name,
            String game
    ) {
        var response = given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of(
                        "name", name,
                        "game", game
                ))
                .when()
                .post("/api/playthroughs")
                .then()
                .statusCode(201)
                .extract()
                .response();

        long id = response.jsonPath().getLong("id");

        createdPlaythroughs.add(
                new CreatedPlaythrough(id, token)
        );

        response.then()
                .body("name", is(name))
                .body("game", is(game));

        return id;
    }

    private static String loginAndGetToken(String username) {
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

    private record CreatedPlaythrough(long id, String token) {
    }
}