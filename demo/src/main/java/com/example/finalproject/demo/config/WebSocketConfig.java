package com.example.finalproject.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketConfig.class);

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        logger.info("Configuring message broker");
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
        logger.info("Message broker configured with broker prefix: /topic, application prefix: /app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        logger.info("Registering STOMP endpoints");
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
        
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
                
        logger.info("STOMP endpoints registered: /ws with and without SockJS support");
    }
    
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public org.springframework.messaging.Message<?> preSend(org.springframework.messaging.Message<?> message, org.springframework.messaging.MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null) {
                    StompCommand command = accessor.getCommand();
                    logger.debug("Received STOMP command: {}", command);
                    
                    if (StompCommand.CONNECT.equals(command)) {
                        logger.info("STOMP CONNECT command received from client");
                    } else if (StompCommand.SUBSCRIBE.equals(command)) {
                        logger.info("STOMP SUBSCRIBE command received, destination: {}", accessor.getDestination());
                    } else if (StompCommand.SEND.equals(command)) {
                        logger.info("STOMP SEND command received, destination: {}", accessor.getDestination());
                    } else if (StompCommand.DISCONNECT.equals(command)) {
                        logger.info("STOMP DISCONNECT command received");
                    }
                }
                return message;
            }
        });
    }
    
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        logger.info("Received a new web socket connection: {}", event.getMessage());
    }
    
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        logger.info("Session disconnected: {}", event.getSessionId());
    }
} 