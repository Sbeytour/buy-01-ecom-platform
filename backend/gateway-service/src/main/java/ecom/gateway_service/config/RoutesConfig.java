package ecom.gateway_service.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RoutesConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("media-objects", r -> r.path("/ecom-media/**")
                        .uri("http://minio:9000"))
                .route("user-service", r -> r.path("/auth/**", "/users/**")
                        .uri("lb://USER-SERVICE"))
                .route("product-service", r -> r.path("/products/**")
                        .uri("lb://PRODUCT-SERVICE"))
                .route("media-service", r -> r.path("/media/**")
                        .uri("lb://MEDIA-SERVICE"))
                .build();
    }
}
