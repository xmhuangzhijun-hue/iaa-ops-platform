package com.iaaops.iam;

import com.iaaops.shared.error.ApiException;
import com.iaaops.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 单进程登录保护；仅短暂锁住计数，口令哈希与数据库操作不持有这把锁。 */
@Component
public class LoginProtection {
    private final LoginProtectionProperties properties;
    private final Clock clock;
    private final Map<String, State> accounts = new HashMap<>();
    private final Map<String, State> addresses = new HashMap<>();
    private Instant nextSweep = Instant.MIN;

    LoginProtection(LoginProtectionProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public synchronized Attempt begin(String username, String address) {
        Instant now = clock.instant();
        if (!now.isBefore(nextSweep) || accounts.size() >= properties.maxEntries()
                || addresses.size() >= properties.maxEntries()) {
            accounts.values().removeIf(state -> state.inFlight == 0 && state.expired(now));
            addresses.values().removeIf(state -> state.inFlight == 0 && state.expired(now));
            nextSweep = now.plusSeconds(60);
        }
        State account = accounts.get(username);
        State ip = addresses.get(address);
        if (account != null) account.refresh(now);
        if (ip != null) ip.refresh(now);
        if ((account != null && account.blocked(now, properties.accountFailures()))
                || (ip != null && ip.blocked(now, properties.ipFailures()))) {
            throw rejected();
        }
        // 已被某个维度拒绝的请求不能再创建新键，避免被锁IP轮换账号耗尽容量。
        // 不淘汰仍有效的锁定记录，否则轮换虚构账号就能挤掉被攻击账号的锁。
        if ((!accounts.containsKey(username) && accounts.size() >= properties.maxEntries())
                || (!addresses.containsKey(address) && addresses.size() >= properties.maxEntries())) {
            throw rejected();
        }
        if (account == null) {
            account = new State(now);
            accounts.put(username, account);
        }
        if (ip == null) {
            ip = new State(now);
            addresses.put(address, ip);
        }
        // 把在途校验一起预占，避免同一账号/IP并发发出许多请求穿过失败阈值。
        account.inFlight++;
        ip.inFlight++;
        return new Attempt(account, ip);
    }

    public synchronized void reset(String username, String address) {
        Instant now = clock.instant();
        State account = accounts.get(username);
        State ip = addresses.get(address);
        if (account != null) account.reset(now);
        if (ip != null) ip.reset(now);
    }

    static ApiException rejected() {
        // 同一种响应覆盖不存在、停用、口令错和锁定，不暴露具体命中了哪个维度。
        return new ApiException(ErrorCode.INVALID_CREDENTIALS, "账号或口令错误");
    }

    public final class Attempt implements AutoCloseable {
        private final State account;
        private final State ip;
        private final long accountGeneration;
        private final long ipGeneration;
        private boolean closed;

        private Attempt(State account, State ip) {
            this.account = account;
            this.ip = ip;
            this.accountGeneration = account.generation;
            this.ipGeneration = ip.generation;
        }

        public void failed() {
            synchronized (LoginProtection.this) {
                if (closed) return;
                Instant now = clock.instant();
                // 成功或改密已清零后，旧的在途请求不能把清零前的失败带回来。
                if (account.generation == accountGeneration) account.failed(now, properties.accountFailures());
                if (ip.generation == ipGeneration) ip.failed(now, properties.ipFailures());
                close();
            }
        }

        @Override
        public void close() {
            synchronized (LoginProtection.this) {
                if (closed) return;
                account.inFlight--;
                ip.inFlight--;
                closed = true;
            }
        }
    }

    private final class State {
        private int failures;
        private int inFlight;
        private Instant windowEnd;
        private Instant lockedUntil;
        private long generation;

        private State(Instant now) { reset(now); }

        private boolean expired(Instant now) {
            return !now.isBefore(lockedUntil == null ? windowEnd : lockedUntil);
        }

        private void refresh(Instant now) {
            if (expired(now)) reset(now);
        }

        private void reset(Instant now) {
            generation++;
            failures = 0;
            lockedUntil = null;
            windowEnd = now.plus(properties.window());
        }

        private boolean blocked(Instant now, int limit) {
            return (lockedUntil != null && now.isBefore(lockedUntil)) || failures + inFlight >= limit;
        }

        private void failed(Instant now, int limit) {
            refresh(now);
            failures++;
            if (failures >= limit && lockedUntil == null) lockedUntil = now.plus(properties.lockDuration());
        }
    }
}
