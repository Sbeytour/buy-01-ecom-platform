package ecom.user_service.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import ecom.user_service.dto.request.LoginRequest;
import ecom.user_service.dto.request.RegisterRequest;
import ecom.user_service.dto.response.AuthResponse;
import ecom.user_service.dto.response.RegisterResponse;
import ecom.user_service.exceptions.EmailAlreadyExistsException;
import ecom.user_service.exceptions.InvalidCredentialsException;
import ecom.user_service.models.Role;
import ecom.user_service.models.User;
import ecom.user_service.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private BCryptPasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId("user-1");
        mockUser.setUsername("testuser");
        mockUser.setEmail("test@test.com");
        mockUser.setPassword("encodedPassword");
        mockUser.setRole(Role.CLIENT);
    }

    @Test
    void register_Success() {

        RegisterRequest request = new RegisterRequest("testuser", "test@test.com", "password", Role.CLIENT);
        
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(mockUser);

        RegisterResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("test@test.com", response.user().email());
        verify(userRepository).save(any(User.class));
        verifyNoInteractions(jwtService, authenticationManager);
    }

    @Test
    void register_ThrowsEmailAlreadyExistsException() {
        RegisterRequest request = new RegisterRequest("testuser", "existing@test.com", "password", Role.CLIENT);
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_Success() {
        LoginRequest request = new LoginRequest("test@test.com", "password");
        Authentication authentication = mock(Authentication.class);
        
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(mockUser);
        when(jwtService.generateToken(mockUser)).thenReturn("mock-jwt-token");

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mock-jwt-token", response.token());
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void login_ThrowsInvalidCredentialsException() {
        LoginRequest request = new LoginRequest("test@test.com", "wrongpassword");
        
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
    }
}