package com.ltm.oanquan.server;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Adapts the legacy newline-delimited TCP protocol to the shared client handler. */
final class TcpClientConnection implements Runnable, ClientTransport {

    private final Socket socket;
    private final ClientHandler handler;
    private volatile BufferedWriter writer;

    TcpClientConnection(Socket socket, ObjectMapper json, TcpGameServer server) {
        this.socket = socket;
        this.handler = new ClientHandler(json, server, this);
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                     socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
                     socket.getOutputStream(), StandardCharsets.UTF_8))) {
            writer = out;
            String line;
            while ((line = reader.readLine()) != null) {
                // Each TCP line is one complete JSON request.
                if (!handler.handleMessage(line)) break;
            }
        } catch (IOException e) {
            if (!socket.isClosed()) System.err.println("TCP client error: " + e.getMessage());
        } finally {
            handler.disconnect();
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Could not close TCP client: " + e.getMessage());
            }
        }
    }

    @Override
    public boolean isOpen() {
        return writer != null && !socket.isClosed() && socket.isConnected();
    }

    @Override
    public synchronized void sendText(String message) throws IOException {
        if (!isOpen()) throw new IOException("TCP client is disconnected");
        writer.write(message);
        writer.newLine();
        writer.flush();
    }
}
