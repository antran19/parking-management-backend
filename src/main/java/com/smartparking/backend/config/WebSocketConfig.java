package com.smartparking.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket config dùng STOMP protocol.
 *
 * Client kết nối:
 *   const socket = new SockJS('http://localhost:8080/ws');
 *   const stompClient = Stomp.over(socket);
 *   stompClient.connect({}, () => {
 *     stompClient.subscribe('/topic/slots/{buildingId}', callback);
 *   });
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Prefix cho các topic client subscribe
        registry.enableSimpleBroker("/topic");
        // Prefix cho message client gửi lên server (nếu cần)
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // Cho phép FE kết nối (dev: localhost:5173)
                .withSockJS();                 // Fallback cho browser không hỗ trợ native WS
    }
}
