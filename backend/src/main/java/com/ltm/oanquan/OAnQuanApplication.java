package com.ltm.oanquan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ltm.oanquan.server.TcpGameServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class OAnQuanApplication {

    public static void main(String[] args) {
        SpringApplication.run(OAnQuanApplication.class, args);
    }

    @Bean(destroyMethod = "close")
    TcpGameServer gameServer(
            ObjectMapper objectMapper,
            @Value("${oanquan.time-ms:20000}") int timeControlMs,
            @Value("${oanquan.reconnect-grace-ms:45000}") int disconnectGraceMs) {
        if (timeControlMs < 10_000 || timeControlMs > 3_600_000) {
            throw new IllegalArgumentException("oanquan.time-ms phải từ 10000 đến 3600000");
        }
        if (disconnectGraceMs < 1_000 || disconnectGraceMs > 600_000) {
            throw new IllegalArgumentException("oanquan.reconnect-grace-ms phải từ 1000 đến 600000");
        }
        return new TcpGameServer(objectMapper, timeControlMs, disconnectGraceMs);
    }
}
