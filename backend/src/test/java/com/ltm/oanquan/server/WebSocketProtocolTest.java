package com.ltm.oanquan.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "oanquan.tcp.port=0")
class WebSocketProtocolTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private TcpTransportServer tcpServer;

    @Test
    void unsupportedActionReturnsJsonErrorOverWebSocket() throws Exception {
        JsonNode response = sendRequest("{\"action\":\"not_supported\"}");
        assertEquals("error", response.path("type").asText());
        assertEquals("Hành động không được hỗ trợ", response.path("message").asText());
    }

    @Test
    void onlineCountReturnsAuthenticatedUserCountOverWebSocket() throws Exception {
        JsonNode response = sendRequest("{\"action\":\"online_count\"}");
        assertEquals("ok", response.path("type").asText());
        assertEquals(0, response.path("data").path("count").asInt());
    }

    @Test
    void communityChatRequiresAuthenticationOverWebSocket() throws Exception {
        for (String action : new String[]{"community_history", "community_send"}) {
            JsonNode response = sendRequest("{\"action\":\"" + action + "\",\"content\":\"hello\"}");
            assertEquals("error", response.path("type").asText());
            assertEquals("Vui lòng đăng nhập", response.path("message").asText());
        }
    }

    @Test
    void legacyTcpProtocolRemainsAvailableAlongsideSpringWebSocket() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", tcpServer.localPort());
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                     socket.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader reader = new BufferedReader(new InputStreamReader(
                     socket.getInputStream(), StandardCharsets.UTF_8))) {
            writer.write("{\"action\":\"not_supported\"}");
            writer.newLine();
            writer.flush();

            JsonNode response = json.readTree(reader.readLine());
            assertEquals("error", response.path("type").asText());
            assertEquals("Hành động không được hỗ trợ", response.path("message").asText());
        }
    }

    private JsonNode sendRequest(String request) throws Exception {
        CompletableFuture<String> response = new CompletableFuture<>();
        WebSocketClient client = new WebSocketClient(URI.create("ws://127.0.0.1:" + port)) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                send(request);
            }

            @Override
            public void onMessage(String message) {
                response.complete(message);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                if (!response.isDone()) {
                    response.completeExceptionally(
                            new IllegalStateException("WebSocket closed before responding: " + reason));
                }
            }

            @Override
            public void onError(Exception error) {
                response.completeExceptionally(error);
            }
        };

        try {
            assertTrue(client.connectBlocking(5, TimeUnit.SECONDS));
            return json.readTree(response.get(5, TimeUnit.SECONDS));
        } finally {
            client.closeBlocking();
        }
    }
}
