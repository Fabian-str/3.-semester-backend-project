package app.config;

import app.entities.Playthrough;
import app.entities.PokemonInstance;
import app.entities.User;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.cfg.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class HibernateConfig {

    private static EntityManagerFactory emf;

    private HibernateConfig() {
    }

    public static EntityManagerFactory getEntityManagerFactory() {

        if (emf == null) {
            emf = buildEntityManagerFactory();
        }

        return emf;
    }

    private static EntityManagerFactory buildEntityManagerFactory() {
        Properties properties = loadProperties();

        String dbHost = getConfigValue(properties, "DB_HOST", "localhost");

        String dbPort = getConfigValue(properties, "DB_PORT", "5432");

        String dbName = getConfigValue(properties, "DB_NAME", null);

        String dbUsername = getConfigValue(properties, "DB_USERNAME", null);

        String dbPassword = getConfigValue(properties, "DB_PASSWORD", null);

        if (dbName == null || dbUsername == null || dbPassword == null) {

            throw new RuntimeException("Database configuration is missing");
        }

        String jdbcUrl = "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName;

        Configuration configuration = new Configuration();

        configuration.setProperty("hibernate.connection.url", jdbcUrl);

        configuration.setProperty("hibernate.connection.username", dbUsername);

        configuration.setProperty("hibernate.connection.password", dbPassword);

        configuration.setProperty("hibernate.connection.driver_class", "org.postgresql.Driver");

        configuration.setProperty("hibernate.hbm2ddl.auto", "update");

        configuration.setProperty("hibernate.show_sql", "true");

        configuration.addAnnotatedClass(Playthrough.class);
        configuration.addAnnotatedClass(PokemonInstance.class);
        configuration.addAnnotatedClass(User.class);

        return configuration.buildSessionFactory();
    }

    private static String getConfigValue(
            Properties properties,
            String key,
            String defaultValue
    ) {
        String environmentValue = System.getenv(key);

        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue;
        }

        String propertyValue = properties.getProperty(key);

        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }

        return defaultValue;
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();

        try (InputStream input = HibernateConfig.class
            .getClassLoader()
            .getResourceAsStream("config.properties")) {

            if (input != null) {
                properties.load(input);
            }

        } catch (IOException e) {
            throw new RuntimeException("Could not load config.properties", e);
        }

        return properties;
    }
}