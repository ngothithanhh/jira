package com.example.jira.service.impl;

import com.example.jira.dto.auth.AuthResponseDTO;
import com.example.jira.dto.auth.LoginRequestDTO;
import com.example.jira.dto.auth.RegisterRequestDTO;
import com.example.jira.dto.user.UserSummary;
import com.example.jira.entity.RefreshToken;
import com.example.jira.entity.User;
import com.example.jira.repository.UserRepository;
import com.example.jira.security.jwt.JwtUtil;
import com.example.jira.service.AuthService;
import com.example.jira.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;

    @Override
    public AuthResponseDTO login(LoginRequestDTO request){
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        User user = userRepository.findByEmail(request.getEmail()).orElseThrow();

        String token = jwtUtil.generateToken(request.getEmail());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

//        System.out.println(token+"\n");
        return new AuthResponseDTO(token, refreshToken.getTokenValue());
    }

    @Override
    public AuthResponseDTO register(RegisterRequestDTO request){

        if(userRepository.existsByEmail(request.getEmail())){
            throw new RuntimeException("Email đã tồn tại!");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setUserName(request.getUserName());
        user.setCreatedAt(LocalDateTime.now());

        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getEmail());

        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return new AuthResponseDTO(token, refreshToken.getTokenValue());

    }

    /** Token từ body được tiêu thụ và rotation trong service có khóa DB; chỉ cấp access token sau commit. */
    @Override
    public AuthResponseDTO refreshToken(String refreshToken){
        var result = refreshTokenService.consumeAndRotate(refreshToken);
        // Việc thu hồi family đã commit trước khi trả 401 để client đăng nhập lại.
        if (result.error() != null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, result.error());
        RefreshToken next = result.token();
        return new AuthResponseDTO(jwtUtil.generateToken(next.getUser().getEmail()), next.getTokenValue());
    }

    @Override
    public void logout(String refreshToken){
        RefreshToken token = refreshTokenService.verifyToken(refreshToken);

        refreshTokenService.revokeToken(token);

    }

    @Override
    public UserSummary getCurrentUser(String email){
        User user = userRepository.findByEmail(email).orElseThrow(()->new RuntimeException("Không tìm thấy người dùng!"));

        return new UserSummary(user.getUserId(), user.getEmail(), user.getUserName());

    }

    @Override
    public UserSummary getCurrentUser(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        User user = userRepository.findByEmail(email).orElseThrow(()->new RuntimeException("Không tìm thấy người dùng!"));

        return new UserSummary(user.getUserId(), user.getEmail(), user.getUserName());
    }


}
