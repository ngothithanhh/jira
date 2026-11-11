package com.example.jira.security.jwt;

import com.example.jira.exception.ApiError;
import com.example.jira.security.CustomUserDetailsService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private CustomUserDetailsService userDetailsService;

    /**
     * Dùng trước controller: Bearer JWT chứa email của người gọi, không phải ID tài nguyên cần truy cập.
     * Parser kiểm chữ ký/hạn dùng trước khi tra users theo email; chỉ token hợp lệ mới được đặt Authentication.
     * Sai token hoặc user đã bị xóa thì xóa danh tính và trả JSON 401 ngay; không gọi controller, không ghi DB.
     * Không có header thì để SecurityConfig quyết định endpoint public hay cần đăng nhập.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // Chỉ bắt lỗi xác thực trong khối JWT; lỗi nghiệp vụ ở filter chain phía sau phải giữ nguyên.
            try {
                String token = authHeader.substring(7);
                String username = jwtUtil.extractUsername(token);
                if (username == null || username.isBlank()) {
                    throw new BadCredentialsException("JWT thiếu chủ thể");
                }
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                if (!userDetails.isEnabled() || !userDetails.isAccountNonLocked()
                        || !userDetails.isAccountNonExpired() || !userDetails.isCredentialsNonExpired()
                        || !jwtUtil.validateToken(token, userDetails.getUsername())) {
                    throw new BadCredentialsException("JWT hoặc tài khoản không hợp lệ");
                }

                // Danh tính lấy từ users theo email đã ký, không lấy từ tham số userId của request.
                var authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException | AuthenticationException exception) {
                // Không trả chi tiết chữ ký/token cho client và không chạy tiếp với danh tính cũ.
                SecurityContextHolder.clearContext();
                ApiError.write(response, HttpStatus.UNAUTHORIZED, "Chưa xác thực hoặc phiên đăng nhập không hợp lệ");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
