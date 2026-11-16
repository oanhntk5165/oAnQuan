package com.ltm.oanquan.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TcpProtocolTest {

    @Test
    void unsupportedActionReturnsJsonError() throws Exception {
        ObjectMapper json = new ObjectMapper();
        try (TcpGameServer server = new TcpGameServer();
             ServerSocket listener = new ServerSocket(0);
             Socket clientSocket = new Socket("127.0.0.1", listener.getLocalPort());
             Socket serverSocket = listener.accept();
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                     clientSocket.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader reader = new BufferedReader(new InputStreamReader(
                     clientSocket.getInputStream(), StandardCharsets.UTF_8))) {
            Thread handler = new Thread(new TcpClientConnection(serverSocket, json, server));
            handler.start();
            writer.write("{\"action\":\"not_supported\"}");
            writer.newLine();
            writer.flush();

            var response = json.readTree(reader.readLine());
            assertEquals("error", response.path("type").asText());
            assertEquals("Hành động không được hỗ trợ", response.path("message").asText());
        }
    }
}
