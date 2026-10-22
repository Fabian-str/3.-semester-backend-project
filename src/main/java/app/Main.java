package app;

import app.config.AppConfig;
import app.config.AppDependencies;
import app.routes.Routes;
import io.javalin.Javalin;

public class Main {

    public static void main(String[] args) {

        AppDependencies dependencies = new AppDependencies();

        Javalin app = AppConfig.create();

        Routes.register(app, dependencies);

        app.start(7071);
    }
}