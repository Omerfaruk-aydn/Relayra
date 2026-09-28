package com.relayra.config;

import com.relayra.realtime.SafeStompErrorHandler;
import com.relayra.realtime.StompAuthenticationInterceptor;
import com.relayra.realtime.StompOutboundAuthorizationInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final StompAuthenticationInterceptor authenticationInterceptor;
  private final StompOutboundAuthorizationInterceptor outboundAuthorizationInterceptor;
  private final SafeStompErrorHandler errorHandler;
  private final String[] allowedOrigins;
  private final ThreadPoolTaskScheduler brokerTaskScheduler;

  public WebSocketConfig(
      StompAuthenticationInterceptor authenticationInterceptor,
      StompOutboundAuthorizationInterceptor outboundAuthorizationInterceptor,
      SafeStompErrorHandler errorHandler,
      @Value("${relayra.websocket.allowed-origin-patterns:http://localhost:5173}")
          String[] allowedOrigins) {
    this.authenticationInterceptor = authenticationInterceptor;
    this.outboundAuthorizationInterceptor = outboundAuthorizationInterceptor;
    this.errorHandler = errorHandler;
    this.allowedOrigins = allowedOrigins;
    this.brokerTaskScheduler = new ThreadPoolTaskScheduler();
    this.brokerTaskScheduler.setPoolSize(1);
    this.brokerTaskScheduler.setThreadNamePrefix("relayra-ws-heartbeat-");
    this.brokerTaskScheduler.initialize();
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry
        .enableSimpleBroker("/topic", "/queue")
        .setTaskScheduler(brokerTaskScheduler)
        .setHeartbeatValue(new long[] {10_000, 10_000});
    registry.setApplicationDestinationPrefixes("/app");
    registry.setUserDestinationPrefix("/user");
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.setErrorHandler(errorHandler);
    registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins);
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.taskExecutor().corePoolSize(4).maxPoolSize(16).queueCapacity(500);
    registration.interceptors(authenticationInterceptor);
  }

  @Override
  public void configureClientOutboundChannel(ChannelRegistration registration) {
    registration.taskExecutor().corePoolSize(4).maxPoolSize(16).queueCapacity(500);
    registration.interceptors(outboundAuthorizationInterceptor);
  }

  @Override
  public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
    registration.setMessageSizeLimit(64 * 1024);
    registration.setSendBufferSizeLimit(128 * 1024);
    registration.setSendTimeLimit(10_000);
  }
}
