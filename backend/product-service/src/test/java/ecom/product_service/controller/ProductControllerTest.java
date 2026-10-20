package ecom.product_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.data.domain.Page;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ecom.product_service.dto.request.ProductRequest;
import ecom.product_service.dto.request.ProductUpdateRequest;
import ecom.product_service.dto.response.ProductResponse;
import ecom.product_service.service.ProductService;
import ecom.product_service.config.SecurityConfig;
import ecom.product_service.exception.GlobalExceptionHandler;
import ecom.product_service.exception.ProductOwnershipException;

@WebMvcTest(controllers = ProductController.class)
@ContextConfiguration(classes = { ProductController.class, SecurityConfig.class, GlobalExceptionHandler.class })
class ProductControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @MockitoBean
        private ProductService productService;

        // Keep the real security rules but avoid loading production key files.
        @MockitoBean
        private JwtDecoder jwtDecoder;

        @Autowired
        private JwtAuthenticationConverter jwtAuthenticationConverter;

        @Test
        void getProductById_ShouldReturn200() throws Exception {
                ProductResponse response = ProductResponse.builder()
                                .id("prod-1")
                                .name("Keyboard")
                                .price(89.99)
                                .quantity(3)
                                .mediaIds(List.of())
                                .sellerId("seller-1")
                                .build();

                when(productService.getProductById("prod-1")).thenReturn(response);

                mockMvc.perform(get("/products/prod-1"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value("prod-1"))
                                .andExpect(jsonPath("$.name").value("Keyboard"));
        }

        @Test
        void createProduct_ShouldReturn201() throws Exception {
                ProductRequest request = new ProductRequest();
                request.setName("Mouse");
                request.setDescription("Gaming");
                request.setPrice(49.99);
                request.setQuantity(5);

                ProductResponse response = ProductResponse.builder()
                                .id("prod-2")
                                .name("Mouse")
                                .description("Gaming")
                                .price(49.99)
                                .quantity(5)
                                .mediaIds(List.of())
                                .sellerId("seller-1")
                                .build();

                when(productService.createProduct(any(ProductRequest.class), eq("seller-1"))).thenReturn(response);

                mockMvc.perform(post("/products")
                                .with(jwt().jwt(token -> token.subject("seller-1"))
                                                .authorities(new SimpleGrantedAuthority("ROLE_SELLER")))
                                .header("X-User-Id", "spoofed-user")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id").value("prod-2"))
                                .andExpect(jsonPath("$.name").value("Mouse"));

                verify(productService).createProduct(any(ProductRequest.class), eq("seller-1"));
        }

        @Test
        void updateProduct_ShouldReturn200() throws Exception {
                ProductUpdateRequest request = new ProductUpdateRequest();
                request.setName("Keyboard Pro");
                request.setPrice(99.99);

                ProductResponse response = ProductResponse.builder()
                                .id("prod-1")
                                .name("Keyboard Pro")
                                .price(99.99)
                                .quantity(3)
                                .mediaIds(List.of())
                                .sellerId("seller-1")
                                .build();

                when(productService.updateProduct(eq("prod-1"), any(ProductUpdateRequest.class), eq("seller-1")))
                                .thenReturn(response);

                mockMvc.perform(put("/products/prod-1")
                                .with(jwt().jwt(token -> token.subject("seller-1"))
                                                .authorities(new SimpleGrantedAuthority("ROLE_SELLER")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name").value("Keyboard Pro"));
        }

        @Test
        void deleteProduct_ShouldReturn204() throws Exception {
                mockMvc.perform(delete("/products/prod-1")
                                .with(jwt().jwt(token -> token.subject("seller-1"))
                                                .authorities(new SimpleGrantedAuthority("ROLE_SELLER"))))
                                .andExpect(status().isNoContent());

                verify(productService).deleteProduct("prod-1", "seller-1");
        }

        @Test
        void getProducts_ShouldAllowAnonymousRequests() throws Exception {
                when(productService.getProducts(0, 10)).thenReturn(Page.empty());
                mockMvc.perform(get("/products")).andExpect(status().isOk());
                verify(productService).getProducts(0, 10);
        }

        @Test
        void getMyProducts_ShouldUseJwtSubjectInsteadOfHeader() throws Exception {
                when(productService.getProductsBySeller("seller-1", 0, 10)).thenReturn(Page.empty());
                mockMvc.perform(get("/products/me")
                                .with(jwt().jwt(token -> token.subject("seller-1")))
                                .header("X-User-Id", "spoofed-user"))
                                .andExpect(status().isOk());
                verify(productService).getProductsBySeller("seller-1", 0, 10);
        }

        @Test
        void protectedEndpoints_ShouldRejectAnonymousRequestsEvenWithIdentityHeader() throws Exception {
                mockMvc.perform(get("/products/me").header("X-User-Id", "seller-1"))
                                .andExpect(status().isUnauthorized());
                mockMvc.perform(post("/products")).andExpect(status().isUnauthorized());
                mockMvc.perform(put("/products/prod-1")).andExpect(status().isUnauthorized());
                mockMvc.perform(delete("/products/prod-1")).andExpect(status().isUnauthorized());
                verifyNoInteractions(productService);
        }

        @Test
        void productWrites_ShouldRejectClientAuthority() throws Exception {
                mockMvc.perform(post("/products")
                                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENT"))))
                                .andExpect(status().isForbidden());
                mockMvc.perform(put("/products/prod-1")
                                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENT"))))
                                .andExpect(status().isForbidden());
                mockMvc.perform(delete("/products/prod-1")
                                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENT"))))
                                .andExpect(status().isForbidden());
                verifyNoInteractions(productService);
        }

        @Test
        void deleteProduct_ShouldReturn403WhenOwnershipCheckFails() throws Exception {
                org.mockito.Mockito.doThrow(new ProductOwnershipException())
                                .when(productService).deleteProduct("prod-1", "other-seller");
                mockMvc.perform(delete("/products/prod-1")
                                .with(jwt().jwt(token -> token.subject("other-seller"))
                                                .authorities(new SimpleGrantedAuthority("ROLE_SELLER"))))
                                .andExpect(status().isForbidden());
        }

        @Test
        void bearerToken_ShouldPassThroughDecoderAndRoleConverter() throws Exception {
                Jwt token = Jwt.withTokenValue("valid-token")
                                .header("alg", "RS256")
                                .subject("seller-1")
                                .claim("role", "SELLER")
                                .build();
                when(jwtDecoder.decode("valid-token")).thenReturn(token);
                mockMvc.perform(delete("/products/prod-1")
                                .header("Authorization", "Bearer valid-token"))
                                .andExpect(status().isNoContent());
                verify(jwtDecoder).decode("valid-token");
                verify(productService).deleteProduct("prod-1", "seller-1");
        }

        @Test
        void bearerToken_ShouldReturn401WhenDecoderRejectsIt() throws Exception {
                when(jwtDecoder.decode("invalid-token")).thenThrow(new BadJwtException("Invalid token"));
                mockMvc.perform(get("/products/me")
                                .header("Authorization", "Bearer invalid-token"))
                                .andExpect(status().isUnauthorized());
                verifyNoInteractions(productService);
        }

        @Test
        void roleConverter_ShouldMapRoleClaimToSellerAuthority() {
                Jwt token = Jwt.withTokenValue("test-token")
                                .header("alg", "RS256")
                                .subject("seller-1")
                                .claim("role", "SELLER")
                                .build();
                org.junit.jupiter.api.Assertions.assertTrue(jwtAuthenticationConverter.convert(token)
                                .getAuthorities().contains(new SimpleGrantedAuthority("ROLE_SELLER")));
        }
}
