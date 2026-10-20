package ecom.gateway_service.config;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.config.EnableWebFlux;

import reactor.core.publisher.Mono;

@SpringJUnitConfig({ SecurityConfig.class, CorsConfig.class, SecurityConfigTest.WebConfig.class })
class SecurityConfigTest {

    @Autowired
    private ApplicationContext context;

    @MockitoBean
    private ReactiveJwtDecoder jwtDecoder;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        // CORS compares request and Origin scheme/host/port, even with a mock server.
        client = WebTestClient.bindToApplicationContext(context)
                .configureClient()
                .baseUrl("https://localhost:8443")
                .build();
    }

    @Test
    void publicEndpoints_ShouldAllowAnonymousRequests() {
        for (String path : new String[] { "/products", "/products/product-1", "/actuator/health",
                "/media/images/product-1", "/media/profile/user-1", "/ecom-media/image.png" }) {
            client.get().uri(path).exchange().expectStatus().isOk();
        }
        for (String path : new String[] { "/auth/login", "/auth/register" }) {
            client.post().uri(path).exchange().expectStatus().isOk();
        }
        client.head().uri("/ecom-media/image.png").exchange().expectStatus().isOk();
    }

    @Test
    void protectedEndpoints_ShouldRequireAuthentication() {
        client.get().uri("/products/me").exchange().expectStatus().isUnauthorized();
        client.get().uri("/users/me").exchange().expectStatus().isUnauthorized();
        client.post().uri("/media/profile").exchange().expectStatus().isUnauthorized();
        client.post().uri("/products").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void invalidBearerToken_ShouldBeRejected() {
        when(jwtDecoder.decode("invalid")).thenReturn(Mono.error(new BadJwtException("Invalid token")));
        client.get().uri("/users/me").headers(headers -> headers.setBearerAuth("invalid"))
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void clientToken_ShouldAllowProfileButDenySellerWrites() {
        stubToken("client", "CLIENT");
        client.get().uri("/users/me").headers(headers -> headers.setBearerAuth("client"))
                .exchange().expectStatus().isOk();
        client.post().uri("/media/profile").headers(headers -> headers.setBearerAuth("client"))
                .exchange().expectStatus().isOk();
        client.post().uri("/products").headers(headers -> headers.setBearerAuth("client"))
                .exchange().expectStatus().isForbidden();
        client.post().uri("/media/images").headers(headers -> headers.setBearerAuth("client"))
                .exchange().expectStatus().isForbidden();
        client.put().uri("/products/product-1").headers(headers -> headers.setBearerAuth("client"))
                .exchange().expectStatus().isForbidden();
        client.delete().uri("/products/product-1").headers(headers -> headers.setBearerAuth("client"))
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void sellerToken_ShouldAllowSellerWrites() {
        stubToken("seller", "SELLER");
        client.post().uri("/products").headers(headers -> headers.setBearerAuth("seller"))
                .exchange().expectStatus().isOk();
        client.post().uri("/media/images").headers(headers -> headers.setBearerAuth("seller"))
                .exchange().expectStatus().isOk();
        client.put().uri("/products/product-1").headers(headers -> headers.setBearerAuth("seller"))
                .exchange().expectStatus().isOk();
        client.delete().uri("/products/product-1").headers(headers -> headers.setBearerAuth("seller"))
                .exchange().expectStatus().isOk();
    }

    @Test
    void authenticatedUnknownEndpoint_ShouldBeDenied() {
        stubToken("seller", "SELLER");
        client.get().uri("/unknown").headers(headers -> headers.setBearerAuth("seller"))
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void allowedPreflight_ShouldNotRequireToken() {
        client.options().uri("/products").header("Origin", "https://localhost:4200")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type")
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "https://localhost:4200");
        verifyNoInteractions(jwtDecoder);
    }

    @Test
    void disallowedOrigin_ShouldBeRejected() {
        client.options().uri("/products").header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "POST")
                .exchange().expectStatus().isForbidden();
    }

    private void stubToken(String value, String role) {
        Jwt token = Jwt.withTokenValue(value).header("alg", "RS256")
                .subject("user-1").claim("role", role).build();
        when(jwtDecoder.decode(value)).thenReturn(Mono.just(token));
    }

    @Configuration
    @EnableWebFlux
    static class WebConfig {
        @Bean
        EchoController echoController() {
            return new EchoController();
        }
    }

    // Test only the security chain, without Eureka or downstream services.
    @RestController
    static class EchoController {
        @RequestMapping("/**")
        Map<String, String> echo() {
            return Map.of("status", "ok");
        }
    }
}
