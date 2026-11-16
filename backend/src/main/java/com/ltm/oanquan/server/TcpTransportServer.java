package com.ltm.oanquan.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Keeps the legacy newline-delimited TCP JSON protocol available alongside Spring WebSocket. */
@Component
final class TcpTransportServer implements SmartLifecycle {

    private final ObjectMapper json;
    private final TcpGameServer application;
    private final int port;
    private final AtomicBoolean running = new AtomicBoolean();
    private final ExecutorService clients = new ThreadPoolExecutor(
            16, 128, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(256),
            new ThreadPoolExecutor.AbortPolicy());
    private volatile ServerSocket listener;
    private volatile Thread acceptThread;

    TcpTransportServer(ObjectMapper json, TcpGameServer application,
                       @Value("${oanquan.tcp.port:5050}") int port) {
        this.json = json;
        this.application = application;
        if (port < 0 || port > 65535) throw new IllegalArgumentException("Cổng TCP không hợp lệ");
        this.port = port;
    }

    @Override
    public synchronized void start() {
        if (!running.compareAndSet(false, true)) return;
        try {
            ServerSocket server = new ServerSocket();
            server.bind(new InetSocketAddress(port));
            listener = server;
            System.out.println("Ô ăn quan TCP server đang lắng nghe tại cổng " + server.getLocalPort());
            acceptThread = new Thread(() -> acceptClients(server), "oanquan-tcp-accept");
            acceptThread.setDaemon(true);
            acceptThread.start();
        } catch (IOException e) {
            running.set(false);
            throw new IllegalStateException("Không thể khởi động TCP server trên cổng " + port, e);
        }
    }

    private void acceptClients(ServerSocket server) {
        while (running.get()) {
            try {
                Socket socket = server.accept();
                socket.setTcpNoDelay(true);
                try {
                    clients.execute(new TcpClientConnection(socket, json, application));
                } catch (java.util.concurrent.RejectedExecutionException e) {
                    socket.close();
                }
            } catch (IOException e) {
                if (running.get()) {
                    System.err.println("TCP accept failed: " + e.getMessage());
                    stop();
                }
            }
        }
    }

    @Override
    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) return;
        ServerSocket server = listener;
        if (server != null) {
            try {
                server.close();
            } catch (IOException e) {
                System.err.println("Could not close TCP listener: " + e.getMessage());
            }
        }
        clients.shutdownNow();
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    int localPort() {
        ServerSocket server = listener;
        if (server == null || !server.isBound()) {
            throw new IllegalStateException("TCP server chưa khởi động");
        }
        return server.getLocalPort();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}
