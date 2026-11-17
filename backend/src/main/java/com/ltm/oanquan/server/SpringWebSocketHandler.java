package com.ltm.oanquan.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/** Adapts Spring WebSocket sessions to the shared JSON request handler. */
@Component
final class SpringWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper json;
    private final TcpGameServer application;
    private final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();

    SpringWebSocketHandler(ObjectMapper json, TcpGameServer application) {
        this.json = json;
        this.application = application;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        WebSocketSession safeSession = new ConcurrentWebSocketSessionDecorator(
                session, 10_000, ClientHandler.MAX_REQUEST_LENGTH * 4);
        ClientTransport transport = new ClientTransport() {
            @Override
            public boolean isOpen() {
                return safeSession.isOpen();
            }

            @Override
            public void sendText(String message) throws IOException {
                if (!safeSession.isOpen()) throw new IOException("WebSocket client is disconnected");
                safeSession.sendMessage(new TextMessage(message));
            }
        };
        clients.put(session.getId(), new ClientHandler(json, application, transport));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        ClientHandler client = clients.get(session.getId());
        if (client != null && !client.handleMessage(message.getPayload())) {
            session.close(CloseStatus.TOO_BIG_TO_PROCESS);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        try {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Only text JSON messages are supported"));
        } catch (IOException e) {
            System.err.println("Could not close WebSocket client after binary message: " + e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        disconnect(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws IOException {
        disconnect(session);
        if (session.isOpen()) session.close(CloseStatus.SERVER_ERROR);
    }

    private void disconnect(WebSocketSession session) {
        ClientHandler client = clients.remove(session.getId());
        if (client != null) client.disconnect();
    }
}
