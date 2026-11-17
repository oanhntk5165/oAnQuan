package com.ltm.oanquan.server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
class WebSocketConfig implements WebSocketConfigurer {

    private final SpringWebSocketHandler handler;
    private final String[] allowedOrigins;

    WebSocketConfig(SpringWebSocketHandler handler,
            @Value("${oanquan.allowed-origins:*}") String allowedOrigins) {
        this.handler = handler;
        this.allowedOrigins = allowedOrigins.split("\\s*,\\s*");
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/").setAllowedOriginPatterns(allowedOrigins);
    }

    @Bean
    ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(ClientHandler.MAX_REQUEST_LENGTH);
        container.setMaxBinaryMessageBufferSize(ClientHandler.MAX_REQUEST_LENGTH);
        return container;
    }
}
