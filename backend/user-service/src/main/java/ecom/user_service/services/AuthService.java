package ecom.user_service.services;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import ecom.user_service.dto.request.LoginRequest;
import ecom.user_service.dto.request.RegisterRequest;
import ecom.user_service.dto.response.AuthResponse;
import ecom.user_service.dto.response.RegisterResponse;
import ecom.user_service.dto.response.UserResponse;
import ecom.user_service.exceptions.EmailAlreadyExistsException;
import ecom.user_service.exceptions.InvalidCredentialsException;
import ecom.user_service.models.User;
import ecom.user_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException("Email already exists");
        }

        User newUser = new User();
        newUser.setUsername(request.username());
        newUser.setEmail(request.email());
        newUser.setPassword(passwordEncoder.encode(request.password()));
        newUser.setRole(request.role());

        User savedUser = userRepository.save(newUser);
        return new RegisterResponse(UserResponse.fromEntity(savedUser));
    }

    public AuthResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));

            User user = (User) authentication.getPrincipal();
            String token = jwtService.generateToken(user);
            return new AuthResponse(token, UserResponse.fromEntity(user));
        } catch (AuthenticationException ex) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
    }
}
