package app.config;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

public class AppConfig {

    public static Javalin create() {

        return Javalin.create(config -> {

            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/public";
                staticFiles.location = Location.CLASSPATH;
            });
        });
    }
}