package ecom.user_service.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ContextConfiguration;

import ecom.user_service.config.SecurityConfig;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ecom.user_service.dto.request.UpdateRequest;
import ecom.user_service.dto.response.UserResponse;
import ecom.user_service.models.Role;
import ecom.user_service.services.UserService;

@WebMvcTest(UserController.class)
@ContextConfiguration(classes = { UserController.class, SecurityConfig.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    // Replace infrastructure while keeping the production security filter chain.
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    // Required while @EnableMongoAuditing remains on SecurityConfig.
    @MockitoBean(name = "mongoMappingContext")
    private MongoMappingContext mongoMappingContext;

    private UserResponse mockUserResponse;
    private final String userId = "user-123";

    @BeforeEach
    void setUp() {
        mockUserResponse = new UserResponse(userId, "user@test.com", "testuser", Role.CLIENT, null);
    }

    @Test
    void getCurrentUser_ShouldReturn200AndUserResponse() throws Exception {
        when(userService.getCurrentUser(userId)).thenReturn(mockUserResponse);

        mockMvc.perform(get("/users/me")
                .with(jwt().jwt(token -> token.subject(userId)))
                .header("X-User-Id", "spoofed-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.email").value("user@test.com"))
                .andExpect(jsonPath("$.username").value("testuser"));

        verify(userService).getCurrentUser(userId);
    }

    @Test
    void updateProfile_ShouldReturn200AndUpdatedUserResponse() throws Exception {
        UpdateRequest request = new UpdateRequest();
        request.setUsername("newuser");
        request.setEmail("newuser@test.com");
        request.setRole(Role.SELLER);

        UserResponse updatedResponse = new UserResponse(userId, "newuser@test.com", "newuser", Role.SELLER, null);
        when(userService.UpdateProfile(eq(userId), any(UpdateRequest.class))).thenReturn(updatedResponse);

        mockMvc.perform(patch("/users/me")
                .with(jwt().jwt(token -> token.subject(userId)))
                .header("X-User-Id", "spoofed-user")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("newuser@test.com"))
                .andExpect(jsonPath("$.username").value("newuser"))
                .andExpect(jsonPath("$.role").value("SELLER"));

        verify(userService).UpdateProfile(eq(userId), any(UpdateRequest.class));
    }

    @Test
    void getCurrentUser_ShouldReturn401WithoutAuthentication() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(userService);
    }

    @Test
    void updateProfile_ShouldRejectIdentityHeaderWithoutAuthentication() throws Exception {
        mockMvc.perform(patch("/users/me")
                .header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"newuser\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(userService);
    }

    @Test
    void getCurrentUser_ShouldAllowBothClientAndSellerTokensWithoutIdentityHeader() throws Exception {
        when(userService.getCurrentUser(userId)).thenReturn(mockUserResponse);
        for (String role : new String[] { "CLIENT", "SELLER" }) {
            mockMvc.perform(get("/users/me")
                    .with(jwt().jwt(token -> token.subject(userId).claim("role", role))
                            .authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(userId));
        }
    }

    @Test
    void getCurrentUser_ShouldUseBearerTokenDecodedByResourceServer() throws Exception {
        Jwt token = Jwt.withTokenValue("valid-token").header("alg", "RS256")
                .subject(userId).claim("role", "CLIENT").build();
        when(jwtDecoder.decode("valid-token")).thenReturn(token);
        when(userService.getCurrentUser(userId)).thenReturn(mockUserResponse);

        mockMvc.perform(get("/users/me")
                .header("Authorization", "Bearer valid-token")
                .header("X-User-Id", "another-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId));
        verify(jwtDecoder).decode("valid-token");
        verify(userService).getCurrentUser(userId);
    }

    @Test
    void protectedEndpoints_ShouldRejectInvalidBearerToken() throws Exception {
        when(jwtDecoder.decode("invalid-token")).thenThrow(new BadJwtException("Invalid token"));
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/users/me").header("Authorization", "Bearer invalid-token")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(userService);
    }

    @Test
    void unmatchedEndpoint_ShouldBeDeniedForAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/users/another-user")
                .with(jwt().jwt(token -> token.subject(userId))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(userService);
    }
}
