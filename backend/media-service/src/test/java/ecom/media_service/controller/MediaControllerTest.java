package ecom.media_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;

import ecom.media_service.config.SecurityConfig;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ecom.media_service.dto.response.MediaImageResponse;
import ecom.media_service.dto.response.ProductImagesResponse;
import ecom.media_service.dto.response.ProfileImageResponse;
import ecom.media_service.service.MediaService;

@WebMvcTest(controllers = MediaController.class)
@ContextConfiguration(classes = { MediaController.class, SecurityConfig.class})
class MediaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MediaService mediaService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void uploadProductImages_ShouldReturn201() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "img.png",
                "image/png",
                "img".getBytes(StandardCharsets.UTF_8));

        ProductImagesResponse response = new ProductImagesResponse(
                "prod-1",
                List.of(new MediaImageResponse("m1", "http://cdn/products/prod-1/a.png")));

        when(mediaService.uploadProductImages(eq("user-1"), eq("prod-1"), any())).thenReturn(response);

        mockMvc.perform(multipart("/media/images")
                .file(file)
                .param("productId", "prod-1")
                .header("X-User-Id", "spoofed-user")
                .with(jwt().jwt(token -> token.subject("user-1"))
                        .authorities(new SimpleGrantedAuthority("ROLE_SELLER"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value("prod-1"))
                .andExpect(jsonPath("$.images[0].id").value("m1"));
        verify(mediaService).uploadProductImages(eq("user-1"), eq("prod-1"), any());
    }

    @Test
    void getProductImages_ShouldReturn200() throws Exception {
        ProductImagesResponse response = new ProductImagesResponse(
                "prod-1",
                List.of(new MediaImageResponse("m1", "http://cdn/products/prod-1/a.png")));

        when(mediaService.getProductImages("prod-1")).thenReturn(response);

        mockMvc.perform(get("/media/images/prod-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value("prod-1"));
    }

    @Test
    void uploadProfileImage_ShouldReturn200() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                "img".getBytes(StandardCharsets.UTF_8));

        ProfileImageResponse response = new ProfileImageResponse(new MediaImageResponse("a1", "http://cdn/avatars/u/a1.png"));
        when(mediaService.uploadProfileImage(eq("user-1"), any())).thenReturn(response);

        mockMvc.perform(multipart("/media/profile")
                .file(file)
                .header("X-User-Id", "spoofed-user")
                .with(jwt().jwt(token -> token.subject("user-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatar.id").value("a1"));
    }

    @Test
    void getProfileImage_ShouldReturn200() throws Exception {
        ProfileImageResponse response = new ProfileImageResponse(new MediaImageResponse("a1", "http://cdn/avatars/u/a1.png"));
        when(mediaService.getProfileImage("user-1")).thenReturn(response);

        mockMvc.perform(get("/media/profile/user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatar.id").value("a1"));
    }

    @Test
    void uploads_ShouldRejectAnonymousRequestsEvenWithSpoofedIdentity() throws Exception {
        mockMvc.perform(multipart("/media/profile").file(avatarFile()).header("X-User-Id", "user-1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/media/images").file(productFile()).param("productId", "prod-1")
                .header("X-User-Id", "user-1"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(mediaService);
    }

    @Test
    void productUpload_ShouldRejectClientRole() throws Exception {
        mockMvc.perform(multipart("/media/images").file(productFile()).param("productId", "prod-1")
                .with(jwt().jwt(token -> token.subject("user-1"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CLIENT"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(mediaService);
    }

    @Test
    void profileUpload_ShouldAcceptClientBearerTokenAndUseItsSubject() throws Exception {
        Jwt token = Jwt.withTokenValue("client-token").header("alg", "RS256")
                .subject("client-1").claim("role", "CLIENT").build();
        when(jwtDecoder.decode("client-token")).thenReturn(token);
        when(mediaService.uploadProfileImage(eq("client-1"), any()))
                .thenReturn(new ProfileImageResponse(new MediaImageResponse("avatar-1", "/avatar")));
        mockMvc.perform(multipart("/media/profile").file(avatarFile())
                .header("Authorization", "Bearer client-token").header("X-User-Id", "victim"))
                .andExpect(status().isOk());
        verify(mediaService).uploadProfileImage(eq("client-1"), any());
    }

    @Test
    void productUpload_ShouldUseRoleConverterForRealBearerAuthentication() throws Exception {
        Jwt token = Jwt.withTokenValue("seller-token").header("alg", "RS256")
                .subject("seller-1").claim("role", "SELLER").build();
        when(jwtDecoder.decode("seller-token")).thenReturn(token);
        when(mediaService.uploadProductImages(eq("seller-1"), eq("prod-1"), any()))
                .thenReturn(new ProductImagesResponse("prod-1", List.of()));
        mockMvc.perform(multipart("/media/images").file(productFile()).param("productId", "prod-1")
                .header("Authorization", "Bearer seller-token"))
                .andExpect(status().isCreated());
        verify(mediaService).uploadProductImages(eq("seller-1"), eq("prod-1"), any());
    }

    @Test
    void upload_ShouldRejectInvalidBearerToken() throws Exception {
        when(jwtDecoder.decode("invalid")).thenThrow(new BadJwtException("Invalid token"));
        mockMvc.perform(multipart("/media/profile").file(avatarFile())
                .header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(mediaService);
    }

    private MockMultipartFile avatarFile() {
        return new MockMultipartFile("file", "avatar.png", "image/png", new byte[] { 1 });
    }

    private MockMultipartFile productFile() {
        return new MockMultipartFile("files", "product.png", "image/png", new byte[] { 1 });
    }
}
