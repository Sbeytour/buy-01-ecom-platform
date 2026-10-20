package ecom.user_service.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.context.ContextConfiguration;

import ecom.user_service.config.SecurityConfig;
import ecom.user_service.exceptions.GlobalExceptionHandler;
import ecom.user_service.exceptions.InvalidCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ecom.user_service.dto.request.LoginRequest;
import ecom.user_service.dto.request.RegisterRequest;
import ecom.user_service.dto.response.AuthResponse;
import ecom.user_service.dto.response.RegisterResponse;
import ecom.user_service.dto.response.UserResponse;
import ecom.user_service.models.Role;
import ecom.user_service.services.AuthService;

@WebMvcTest(AuthController.class)
@ContextConfiguration(classes = { AuthController.class, SecurityConfig.class, GlobalExceptionHandler.class })
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    // Replace infrastructure while keeping the production security filter chain.
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    // Required while @EnableMongoAuditing remains on SecurityConfig.
    @MockitoBean(name = "mongoMappingContext")
    private MongoMappingContext mongoMappingContext;

    private AuthResponse mockAuthResponse;
    private RegisterResponse mockRegisterResponse;

    @BeforeEach
    void setUp() {
        UserResponse userResponse = new UserResponse("user-1", "test@test.com", "testuser", Role.CLIENT, null);
        mockAuthResponse = new AuthResponse("mock-jwt-token", userResponse);
        mockRegisterResponse = new RegisterResponse(userResponse);
    }

    @Test
    void register_ShouldReturn201AndRegisterResponse() throws Exception {
        RegisterRequest request = new RegisterRequest("testuser", "test@test.com", "password", Role.CLIENT);

        when(authService.register(any(RegisterRequest.class))).thenReturn(mockRegisterResponse);

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value("test@test.com"))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void login_ShouldReturn201AndAuthResponse() throws Exception {
        LoginRequest request = new LoginRequest("test@test.com", "password");

        when(authService.login(any(LoginRequest.class))).thenReturn(mockAuthResponse);

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("mock-jwt-token"))
                .andExpect(jsonPath("$.user.username").value("testuser"));
    }

    @Test
    void login_ShouldReturn401ForInvalidCredentials() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new InvalidCredentialsException("Invalid email or password"));
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("test@test.com", "wrong-password"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_ShouldRejectInvalidInputBeforeCallingService() throws Exception {
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(authService);
    }
}
