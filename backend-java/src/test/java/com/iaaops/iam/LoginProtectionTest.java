package com.iaaops.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iaaops.shared.error.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class LoginProtectionTest {
    private final MutableClock clock = new MutableClock();

    @Test
    void 并发请求不能穿过账号或来源的在途额度() throws Exception {
        for (boolean accountLimit : List.of(true, false)) {
            LoginProtection protection = protection(accountLimit ? 3 : 50, accountLimit ? 50 : 3, 100);
            CountDownLatch ready = new CountDownLatch(20);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<LoginProtection.Attempt>> futures = new ArrayList<>();
            List<LoginProtection.Attempt> admitted = new ArrayList<>();
            try (var pool = Executors.newFixedThreadPool(20)) {
                for (int index = 0; index < 20; index++) {
                    int number = index;
                    futures.add(pool.submit(() -> {
                        ready.countDown();
                        if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("启动门未打开");
                        try {
                            return protection.begin(accountLimit ? "account" : "account-" + number,
                                    accountLimit ? "ip-" + number : "ip");
                        } catch (ApiException rejected) {
                            return null;
                        }
                    }));
                }
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                for (Future<LoginProtection.Attempt> future : futures) {
                    LoginProtection.Attempt attempt = future.get(10, TimeUnit.SECONDS);
                    if (attempt != null) admitted.add(attempt);
                }
                assertThat(admitted).hasSize(3);
            } finally {
                start.countDown();
                admitted.forEach(LoginProtection.Attempt::close);
            }
            // 仅在途额度不足不算失败，释放后无需等锁期。
            try (var ignored = protection.begin("account", "ip")) {
                assertThat(ignored).isNotNull();
            }
        }
    }

    @Test
    void 容量耗尽不会挤掉已有锁且到期可回收() {
        LoginProtection protection = protection(1, 100, 2);
        protection.begin("locked", "ip1").failed();
        protection.begin("other", "ip2").close();
        denied(protection, "new", "ip3");
        denied(protection, "locked", "ip1");
        clock.advance(Duration.ofMinutes(15));
        try (var ignored = protection.begin("new", "ip3")) {
            assertThat(ignored).isNotNull();
        }
    }

    @Test
    void 已锁来源轮换账号或已锁账号轮换来源不消耗新键容量() {
        LoginProtection protection = protection(1, 1, 2);
        protection.begin("locked", "locked-ip").failed();
        for (int index = 0; index < 20; index++) {
            denied(protection, "rotating-" + index, "locked-ip");
            denied(protection, "locked", "rotating-ip-" + index);
        }
        try (var ignored = protection.begin("healthy", "healthy-ip")) {
            assertThat(ignored).isNotNull();
        }
    }

    @Test
    void 在途记录即使超过窗口也不能被容量清理删除() {
        LoginProtection protection = protection(1, 1, 1);
        var ongoing = protection.begin("ongoing", "ip");
        clock.advance(Duration.ofDays(1));
        denied(protection, "new", "new-ip");
        ongoing.close();
        try (var ignored = protection.begin("new", "new-ip")) {
            assertThat(ignored).isNotNull();
        }
    }

    @Test
    void 关闭或失败回调重复调用不重复释放和累计() {
        LoginProtection protection = protection(2, 20, 10);
        var first = protection.begin("account", "ip");
        first.failed();
        first.failed();
        first.close();
        var second = protection.begin("account", "ip");
        second.close();
        second.close();
        var third = protection.begin("account", "ip");
        denied(protection, "account", "ip");
        third.failed();
        denied(protection, "account", "other-ip");
    }

    @Test
    void 重置前启动的迟到失败不会复活已清零计数() {
        LoginProtection protection = protection(2, 2, 10);
        var old = protection.begin("account", "ip");
        protection.reset("account", "ip");
        old.failed();
        protection.begin("account", "ip").failed();
        try (var ignored = protection.begin("account", "ip")) {
            assertThat(ignored).isNotNull();
        }
    }

    @Test
    void 重置不释放仍在执行的校验额度() {
        LoginProtection protection = protection(1, 1, 10);
        var old = protection.begin("account", "ip");
        protection.reset("account", "ip");
        denied(protection, "account", "other-ip");
        denied(protection, "other-account", "ip");
        old.close();
        try (var ignored = protection.begin("account", "ip")) {
            assertThat(ignored).isNotNull();
        }
    }

    @Test
    void 账号大小写按数据库匹配语义独立计数() {
        LoginProtection protection = protection(1, 20, 10);
        protection.begin("account", "ip").failed();
        denied(protection, "account", "ip");
        try (var ignored = protection.begin("ACCOUNT", "ip")) {
            assertThat(ignored).isNotNull();
        }
    }

    private LoginProtection protection(int accounts, int addresses, int capacity) {
        return new LoginProtection(new LoginProtectionProperties(accounts, addresses,
                Duration.ofMinutes(15), Duration.ofMinutes(15), capacity), clock);
    }

    private static void denied(LoginProtection protection, String account, String address) {
        assertThatThrownBy(() -> protection.begin(account, address)).isInstanceOf(ApiException.class)
                .hasMessage("账号或口令错误");
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2030-01-01T00:00:00Z");

        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant, zone); }
        @Override public Instant instant() { return instant; }
    }
}
