package com.example.jira.controller.auth;

import com.example.jira.dto.auth.AuthResponseDTO;
import com.example.jira.dto.auth.LoginRequestDTO;
import com.example.jira.dto.auth.RefreshRequestDTO;
import com.example.jira.dto.auth.RegisterRequestDTO;
import com.example.jira.dto.user.UserSummary;
import com.example.jira.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public AuthResponseDTO login(@RequestBody LoginRequestDTO request){
        return authService.login(request);
    }

    @PostMapping("/register")
    public AuthResponseDTO register(@RequestBody RegisterRequestDTO request){
        return authService.register(request);
    }

    @PostMapping("/refresh")
    public AuthResponseDTO refresh(@RequestBody RefreshRequestDTO request){
        return authService.refreshToken(request.getRefreshToken());

    }

    @PostMapping("/logout")
    public void logout(@RequestBody RefreshRequestDTO request){
        authService.logout(request.getRefreshToken());
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(new Object() {
                public String getMessage() { return "Unauthorized"; }
            });
        }

//        System.out.println("AUTH: " + authentication);

        String email = authentication.getName();
        UserSummary userDto = authService.getCurrentUser(email);

        // Wrap thành { user: {...} } để match FE expectation
        Map<String, Object> response = new HashMap<>();
        response.put("user", userDto);

        return ResponseEntity.ok(response);
    }

}
