package app.services;

import app.dto.GameStateDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.websocket.WsContext;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GameStateWebSocketService {

    private final Set<WsContext> clients =
            ConcurrentHashMap.newKeySet();

    private final ObjectMapper objectMapper;

    public GameStateWebSocketService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void addClient(WsContext client) {
        clients.add(client);
    }

    public void removeClient(WsContext client) {
        clients.remove(client);
    }

    public void broadcast(Object message) {
        try {
            String json = objectMapper.writeValueAsString(message);

            for (WsContext client : clients) {
                client.send(json);
            }

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Could not serialize WebSocket message", e);
        }
    }
}