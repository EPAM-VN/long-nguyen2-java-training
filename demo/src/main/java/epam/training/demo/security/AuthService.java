package epam.training.demo.security;

import epam.training.demo.security.dto.LoginRequest;
import epam.training.demo.security.dto.RegisterRequest;
import epam.training.demo.user.Role;
import epam.training.demo.user.User;
import epam.training.demo.user.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                        AuthenticationManager authenticationManager, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public User register(RegisterRequest request) {
        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setCreatedAt(Instant.now());
        user.setRoles(Set.of(Role.USER));
        // No pre-check for an existing username - uq_users_username (V1) is
        // the actual source of truth; letting the DB reject a duplicate and
        // translating that into a clean response (GlobalExceptionHandler)
        // avoids a check-then-insert race between two concurrent
        // registrations for the same username.
        return userRepository.save(user);
    }

    public String login(LoginRequest request) {
        // Throws BadCredentialsException (an AuthenticationException) on
        // failure - nothing to catch here, GlobalExceptionHandler maps it.
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        return jwtService.generateToken(authentication.getName(), roles);
    }
}
