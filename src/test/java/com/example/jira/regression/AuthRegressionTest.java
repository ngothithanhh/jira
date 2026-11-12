package com.example.jira.regression;

import com.example.jira.dto.auth.*;
import com.example.jira.entity.*;
import com.example.jira.repository.*;
import com.example.jira.security.jwt.JwtUtil;
import com.example.jira.service.RefreshTokenService;
import com.example.jira.service.impl.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthRegressionTest {
    @AfterEach void clearIdentity() { SecurityContextHolder.clearContext(); }

    @Nested @ExtendWith(MockitoExtension.class)
    class Account {
        @Mock jakarta.persistence.EntityManager entityManager;
        @Mock UserRepository users;
        @Mock AuthenticationManager manager;
        @Mock JwtUtil jwt;
        @Mock RefreshTokenService tokens;
        @Spy PasswordEncoder encoder = new BCryptPasswordEncoder(4);
        @InjectMocks AuthServiceImpl service;

        // Đăng ký lưu hash thay mật khẩu rõ; chỉ cấp token sau khi lưu user.
        @Test void registerPersistsEncodedPasswordBeforeIssuingTokens() {
            var req = new RegisterRequestDTO(); req.setEmail("new@test.invalid"); req.setPassword("sample-password");
            req.setUserName("new"); req.setFullName("New User");
            var refresh = new RefreshToken(); refresh.setTokenValue("refresh");
            when(tokens.createRefreshToken(any())).thenReturn(refresh);
            when(jwt.generateToken(req.getEmail())).thenReturn("access");
            service.register(req);
            var captured = ArgumentCaptor.forClass(User.class); verify(users).save(captured.capture());
            assertThat(encoder.matches("sample-password", captured.getValue().getPasswordHash())).isTrue();
            assertThat(captured.getValue().getCreatedAt()).isNotNull();
            var order = inOrder(users, tokens); order.verify(users).save(any()); order.verify(tokens).createRefreshToken(any());
        }
        // Email trùng phải dừng trước lưu tài khoản và trước cấp refresh token.
        @Test void duplicateEmailDoesNotWrite() {
            var req = new RegisterRequestDTO(); req.setEmail("existing@test.invalid");
            when(users.existsByEmail(req.getEmail())).thenReturn(true);
            assertThatThrownBy(() -> service.register(req)).isInstanceOf(com.example.jira.exception.ConflictException.class);
            verify(users, never()).save(any()); verifyNoInteractions(tokens, jwt);
        }
        // Sai mật khẩu dừng ở AuthenticationManager, không cấp phiên làm việc.
        @Test void wrongPasswordDoesNotIssueTokens() {
            var req = new LoginRequestDTO(); req.setEmail("a@test.invalid"); req.setPassword("wrong");
            var user = Fixtures.user(1);
            when(users.findByEmail(req.getEmail())).thenReturn(Optional.of(user)); when(users.findByIdForUpdate(1)).thenReturn(Optional.of(user));
            when(manager.authenticate(any())).thenThrow(new BadCredentialsException("wrong password"));
            assertThatThrownBy(() -> service.login(req)).isInstanceOf(BadCredentialsException.class);
            verifyNoInteractions(tokens, jwt);
        }
        // Đăng nhập hợp lệ dùng đúng email đã xác thực để tìm user và tạo token.
        @Test void loginIssuesTokensForAuthenticatedUser() {
            var req = new LoginRequestDTO(); req.setEmail("user1@test.invalid"); req.setPassword("password");
            var u = Fixtures.user(1); var token = new RefreshToken(); token.setTokenValue("refresh");
            when(users.findByEmail(req.getEmail())).thenReturn(Optional.of(u));
            when(users.findByIdForUpdate(1)).thenReturn(Optional.of(u));
            when(tokens.createRefreshToken(u)).thenReturn(token);
            when(jwt.generateToken(req.getEmail(),0)).thenReturn("access");
            assertThat(service.login(req)).isNotNull();
            verify(manager).authenticate(any()); verify(tokens).createRefreshToken(u);
        }
        // Refresh nhận kết quả đã commit từ luồng tiêu thụ token có khóa, sau đó mới cấp access token.
        @Test void refreshVerifiesThenRotates() {
            var next = new RefreshToken(); next.setTokenValue("next"); next.setUser(Fixtures.user(1));
            when(tokens.consumeAndRotate("old")).thenReturn(new RefreshTokenService.RotationResult(next, null));
            service.refreshToken("old");
            var order = inOrder(tokens, jwt); order.verify(tokens).consumeAndRotate("old");
            order.verify(jwt).generateToken("user1@test.invalid",0);
        }
        // Reuse đã được ghi nhận trong transaction phải trả 401 và không cấp access token.
        @Test void reuseDoesNotIssueAccessToken() {
            when(tokens.consumeAndRotate("old")).thenReturn(new RefreshTokenService.RotationResult(null, "Refresh token reuse detected"));
            assertThatThrownBy(() -> service.refreshToken("old")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            verifyNoInteractions(jwt);
        }
        // Logout chỉ thu hồi token đã qua kiểm tra.
        @Test void logoutRevokesVerifiedToken() {
            service.logout("value"); verify(tokens).revokeSession("value");
        }
        // /me trả danh tính từ context thay vì nhận ID tùy ý của client.
        @Test void currentUserUsesAuthenticatedEmail() {
            Fixtures.login(1); when(users.findByEmail("user1@test.invalid")).thenReturn(Optional.of(Fixtures.user(1)));
            assertThat(service.getCurrentUser()).isNotNull(); verify(users).findByEmail("user1@test.invalid");
        }
    }

    @Nested @ExtendWith(MockitoExtension.class)
    class RefreshTokens {
        @Mock UserRepository users;
        @Mock jakarta.persistence.EntityManager entityManager;
        @BeforeEach void ownerLock() {
            lenient().when(users.findByIdForUpdate(1)).thenReturn(Optional.of(Fixtures.user(1)));
        }
        @Mock RefreshTokenRepository repository;
        @InjectMocks RefreshTokenServiceImpl service;
        // Token khóa được còn hạn chỉ sinh một con cùng family và thu hồi bản cũ.
        @Test void atomicRotationConsumesLockedToken() {
            var old = new RefreshToken(); old.setUser(Fixtures.user(1)); old.setFamilyId("A"); old.setExpiresAt(LocalDateTime.now().plusDays(1));
            when(repository.findByTokenValue("old")).thenReturn(Optional.of(old));
            when(repository.findByTokenValueForUpdate("old")).thenReturn(Optional.of(old));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
            var result = service.consumeAndRotate("old"); assertThat(result.error()).isNull();
            assertThat(old.isRevoked()).isTrue(); assertThat(result.token().getFamilyId()).isEqualTo("A");
        }
        // Lượt thứ hai dùng token cũ trả lỗi sau khi thu hồi các token cùng family.
        @Test void atomicReuseCommitsFamilyRevocationResult() {
            var old = new RefreshToken(); old.setUser(Fixtures.user(1)); old.setRevoked(true); old.setFamilyId("A");
            var child = new RefreshToken(); child.setFamilyId("A");
            when(repository.findByTokenValueForUpdate("old")).thenReturn(Optional.of(old));
            when(repository.findByTokenValue("old")).thenReturn(Optional.of(old));
            when(repository.findByFamilyId("A")).thenReturn(List.of(old, child));
            assertThat(service.consumeAndRotate("old").error()).contains("reuse");
            assertThat(child.isRevoked()).isTrue(); verify(repository, never()).save(any());
        }
        // Tạo token lưu chủ sở hữu, family và hạn dùng, không chứa mật khẩu.
        @Test void createRefreshTokenInitializesFamilyAndExpiry() {
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
            var u = Fixtures.user(1); var before = LocalDateTime.now(); var t = service.createRefreshToken(u);
            assertThat(t.getUser()).isSameAs(u); assertThat(t.getTokenValue()).isNotBlank();
            assertThat(t.getFamilyId()).isNotBlank(); assertThat(t.getJti()).isNotBlank();
            assertThat(t.isRevoked()).isFalse(); assertThat(t.getExpiresAt()).isAfter(before.plusDays(6));
        }
        // Không tìm thấy token phải dừng, không lưu bản ghi thay thế.
        @Test void missingTokenIsRejected() {
            assertThatThrownBy(() -> service.verifyToken("missing")).isInstanceOf(RuntimeException.class);
            verify(repository, never()).save(any());
        }
        // Token quá hạn không được dùng để tạo phiên mới.
        @Test void expiredTokenIsRejected() {
            var t = new RefreshToken(); t.setExpiresAt(LocalDateTime.now().minusMinutes(1));
            when(repository.findByTokenValue("old")).thenReturn(Optional.of(t));
            assertThatThrownBy(() -> service.verifyToken("old")).hasMessageContaining("expired");
            verify(repository, never()).save(any());
        }
        // Token bị dùng lại phải thu hồi mọi token trong family, không ảnh hưởng family khác.
        @Test void reusedTokenRevokesItsFamily() {
            var old = new RefreshToken(); old.setRevoked(true); old.setFamilyId("family-A");
            var child = new RefreshToken(); child.setFamilyId("family-A");
            when(repository.findByTokenValue("old")).thenReturn(Optional.of(old));
            when(repository.findByFamilyId("family-A")).thenReturn(List.of(old, child));
            assertThatThrownBy(() -> service.verifyToken("old")).hasMessageContaining("reuse");
            assertThat(child.isRevoked()).isTrue(); assertThat(child.getRevokedAt()).isNotNull();
            verify(repository).saveAll(List.of(old, child));
        }
        // Token còn hạn cập nhật lần dùng cuối và trả đúng bản ghi đã kiểm tra.
        @Test void validTokenRecordsLastUse() {
            var t = new RefreshToken(); t.setExpiresAt(LocalDateTime.now().plusDays(1));
            when(repository.findByTokenValue("valid")).thenReturn(Optional.of(t));
            when(repository.save(t)).thenReturn(t);
            assertThat(service.verifyToken("valid")).isSameAs(t); assertThat(t.getLastUsedAt()).isNotNull();
        }
        // Rotation giữ chủ sở hữu và family nhưng sinh giá trị token khác.
        @Test void rotationRevokesOldTokenAndPreservesFamily() {
            var t = new RefreshToken(); t.setUser(Fixtures.user(1)); t.setFamilyId("family-A"); t.setTokenValue("old");
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
            var next = service.rotateRefreshToken(t);
            assertThat(t.isRevoked()).isTrue(); assertThat(t.getRevokedAt()).isNotNull();
            assertThat(next.getFamilyId()).isEqualTo("family-A"); assertThat(next.getUser()).isSameAs(t.getUser());
            assertThat(next.getTokenValue()).isNotEqualTo("old"); assertThat(next.isRevoked()).isFalse();
            verify(repository, times(2)).save(any());
        }
        // Thu hồi một token ghi cờ và thời điểm thu hồi.
        @Test void revokeRecordsTimestamp() {
            var t = new RefreshToken(); service.revokeToken(t);
            assertThat(t.isRevoked()).isTrue(); assertThat(t.getRevokedAt()).isNotNull(); verify(repository).save(t);
        }
    }
}
