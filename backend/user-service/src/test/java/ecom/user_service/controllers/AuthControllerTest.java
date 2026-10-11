package ecom.user_service.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
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
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

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
                .andExpect(jsonPath("$.user.email").value("test@test.com"));
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
}
