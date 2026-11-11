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
    private final jakarta.persistence.EntityManager entityManager;

    /** Khóa/refresh user theo email trước kiểm mật khẩu và mint token, để đổi mật khẩu không xen giữa hai bước. */
    @Override
    @org.springframework.transaction.annotation.Transactional
    public AuthResponseDTO login(LoginRequestDTO request){
        User found = userRepository.findByEmail(request.getEmail()).orElseThrow(() -> new org.springframework.security.authentication.BadCredentialsException("Sai thông tin đăng nhập"));
        User user = userRepository.findByIdForUpdate(found.getUserId()).orElseThrow(() -> new org.springframework.security.authentication.BadCredentialsException("Sai thông tin đăng nhập"));
        entityManager.refresh(user);
        if (!user.isEnabled()) throw new org.springframework.security.authentication.DisabledException("Tài khoản bị khóa");
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        String token = jwtUtil.generateToken(user.getEmail(),user.getAuthVersion());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

//        System.out.println(token+"\n");
        return new AuthResponseDTO(token, refreshToken.getTokenValue());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
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
        return new AuthResponseDTO(jwtUtil.generateToken(next.getUser().getEmail(),next.getUser().getAuthVersion()), next.getTokenValue());
    }

    @Override
    public void logout(String refreshToken){
        // Một phiên có thể vừa rotation: thu hồi family dưới cùng khóa user, không chỉ cờ của token cha.
        refreshTokenService.revokeSession(refreshToken);

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
