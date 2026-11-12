package com.example.jira.verification;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Kiểm giá trị bind SQL khi xác minh qua ranh giới ngày; kiểm DB thật nằm trong EmailVerificationIT. */
@ResourceLock("JVM_DEFAULT_TIME_ZONE")
class VerificationStoreTimeTest {
    @ParameterizedTest
    @CsvSource({"UTC,2026-11-12T17:30", "Asia/Ho_Chi_Minh,2026-11-13T00:30"})
    void auditUsesServerLocalTimeWhileVerifiedInstantRemainsUtc(String zone, String expected) {
        TimeZone original = TimeZone.getDefault();
        try {
            // Giả lập đúng giờ JVM của deployment; khôi phục ở finally để không ảnh hưởng test khác.
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            var db = mock(JdbcTemplate.class);
            var store = new VerificationStore(db);
            Instant now = Instant.parse("2026-11-12T17:30:00Z");
            store.audit(null,25,now);
            store.verifyAccount(25,now);
            // Audit cùng quy ước LocalDateTime của log cũ, còn timestamp xác minh giữ nguyên Instant.
            verify(db).update(contains("INSERT INTO audit_logs"),isNull(),eq(25),eq(LocalDateTime.parse(expected)));
            verify(db).update(eq("UPDATE users SET email_verified_at=? WHERE user_id=?"),eq(now.atOffset(ZoneOffset.UTC)),eq(25));
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
