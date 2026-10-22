package app.services;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EmulatorConnectionService {

    private final Map<String, EmulatorConnection> connections = new ConcurrentHashMap<>();

    public EmulatorConnection createConnection(
            Long userId,
            Long playthroughId
    ) {
        connections.values().removeIf(connection ->
                connection.expiresAt().isBefore(Instant.now())
        );

        String connectionCode = UUID.randomUUID().toString();

        EmulatorConnection connection = new EmulatorConnection(
            connectionCode,
            userId,
            playthroughId,
            Instant.now().plus(1, ChronoUnit.HOURS)
        );

        connections.put(connectionCode, connection);

        return connection;
    }

    public EmulatorConnection findByCode(String connectionCode) {

        if (connectionCode == null || connectionCode.isBlank()) {
            return null;
        }

        EmulatorConnection connection = connections.get(connectionCode);

        if (connection == null) {
            return null;
        }

        if (!Instant.now().isBefore(connection.expiresAt())) {
            connections.remove(connectionCode, connection);
            return null;
        }

        return connection;
    }

    public void disconnect(String connectionCode) {
        if (connectionCode != null) {
            connections.remove(connectionCode);
        }
    }

    public record EmulatorConnection(
        String connectionCode,
        Long userId,
        Long playthroughId,
        Instant expiresAt
    ) {
    }
}