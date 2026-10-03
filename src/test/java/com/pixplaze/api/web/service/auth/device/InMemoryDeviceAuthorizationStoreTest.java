package com.pixplaze.api.web.service.auth.device;

import com.pixplaze.api.web.data.auth.DeviceAuthorizationDecision;
import com.pixplaze.api.web.data.auth.DeviceAuthorizationStatus;
import com.pixplaze.api.web.service.auth.device.DeviceAuthorizationStore.DecisionOutcome;
import com.pixplaze.api.web.service.auth.device.model.ApproverDecision;
import com.pixplaze.api.web.service.auth.device.model.DeviceAuthorizationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Тесты in-memory стора. Время задаёт управляемый {@link MutableClock}, поэтому тесты не зависят
/// ни от разрешения часов, ни от скорости машины.
class InMemoryDeviceAuthorizationStoreTest {

    private static final String HASH = "device-code-hash";
    private static final String USER_CODE = "ABCD2345";
    private static final String DETAILS = "{\"host\":\"example\"}";
    private static final Duration TTL = Duration.ofSeconds(300);
    private static final Duration INTERVAL = Duration.ofSeconds(5);
    private static final Duration DECISION_TTL = Duration.ofSeconds(15);
    private static final int BUDGET = 3;

    private final MutableClock clock = new MutableClock();
    private final InMemoryDeviceAuthorizationStore store = new InMemoryDeviceAuthorizationStore(clock);

    private static DeviceAuthorizationRequest request(String hash, String userCode) {
        return new DeviceAuthorizationRequest("client", userCode, hash, "AAD:USER");
    }

    private static ApproverDecision decision(DeviceAuthorizationDecision decision) {
        return new ApproverDecision(decision, 1L, "127.0.0.1");
    }

    private void createLive() {
        assertTrue(store.tryCreate(request(HASH, USER_CODE), DETAILS, BUDGET, TTL));
    }

    /// Опрос живой записи: пусто здесь — уже провал теста, а не проверяемый исход.
    private boolean tooSoon() {
        return store.readAndConsumeAttempt(HASH, INTERVAL)
                .orElseThrow(() -> new AssertionError("запись должна быть жива"))
                .isTooSoon();
    }

    private DecisionOutcome decide(DeviceAuthorizationDecision candidate) {
        return store.putDecisionIfAbsent(HASH, decision(candidate), DECISION_TTL)
                .orElseThrow(() -> new AssertionError("запись должна быть жива"));
    }

    @Test
    @DisplayName("Запись находится по user-коду вместе с деталями")
    void findsByUserCodeWithDetails() {
        createLive();

        final var state = store.findByUserCode(USER_CODE).orElseThrow();

        assertEquals(DETAILS, state.details());
        assertEquals(DeviceAuthorizationStatus.PENDING, state.status());
    }

    @Test
    @DisplayName("Живой user-код второй раз не выдаётся, истёкший — освобождается")
    void userCodeIsUniqueWhileLive() {
        createLive();

        assertFalse(store.tryCreate(request("other", USER_CODE), null, BUDGET, TTL));

        clock.advance(TTL);
        assertTrue(store.tryCreate(request("other", USER_CODE), null, BUDGET, TTL));
    }

    @Test
    @DisplayName("Истёкшая запись не видна ни одной операции ещё до уборки")
    void expiredIsInvisibleBeforeEviction() {
        createLive();
        clock.advance(TTL);

        assertTrue(store.findByUserCode(USER_CODE).isEmpty());
        assertTrue(store.readAndConsumeAttempt(HASH, INTERVAL).isEmpty(), "истечение — не slow_down");
        assertTrue(store.putDecisionIfAbsent(HASH, decision(DeviceAuthorizationDecision.ALLOW), DECISION_TTL).isEmpty());
        assertTrue(store.takeAndRemove(HASH).isEmpty());
    }

    @Test
    @DisplayName("Уборщик освобождает только истёкшие записи")
    void evictsOnlyExpired() {
        createLive();
        assertEquals(0, store.evictExpired());

        clock.advance(TTL);
        assertEquals(1, store.evictExpired());
    }

    @Test
    @DisplayName("Ранний опрос не ставит отметку и не сдвигает окно")
    void earlyPollDoesNotShiftWindow() {
        createLive();

        assertFalse(tooSoon(), "первый опрос принимается");

        clock.advance(INTERVAL.minusSeconds(1));
        assertTrue(tooSoon(), "интервал ещё не прошёл");

        clock.advance(Duration.ofSeconds(1));
        assertFalse(tooSoon(), "окно отсчитано от принятого опроса");
    }

    @Test
    @DisplayName("Исчерпание бюджета отличимо от отсутствия записи")
    void budgetExhaustionIsDistinct() {
        createLive();

        for (var i = 0; i < BUDGET; i++) {
            store.readAndConsumeAttempt(HASH, Duration.ZERO);
        }

        final var exhausted = store.readAndConsumeAttempt(HASH, Duration.ZERO).orElseThrow(
                () -> new AssertionError("запись жива — это не «записи нет»"));

        assertTrue(exhausted.state().attemptsLeft() < 0, "этот опрос вышел за бюджет");
    }

    @Test
    @DisplayName("Решение принимается один раз и не перезаписывается")
    void decisionIsWrittenOnce() {
        createLive();

        assertEquals(DecisionOutcome.DECIDED, decide(DeviceAuthorizationDecision.DENY));
        assertEquals(DecisionOutcome.ALREADY_DECIDED, decide(DeviceAuthorizationDecision.ALLOW));
        assertEquals(DeviceAuthorizationStatus.DENIED, store.findByUserCode(USER_CODE).orElseThrow().status());
    }

    @Test
    @DisplayName("Решение сокращает срок жизни и никогда его не продлевает")
    void decisionOnlyShortensTtl() {
        createLive();
        store.putDecisionIfAbsent(HASH, decision(DeviceAuthorizationDecision.ALLOW), DECISION_TTL);

        clock.advance(DECISION_TTL);
        assertTrue(store.findByUserCode(USER_CODE).isEmpty(), "одобренная запись гаснет в узком окне");

        assertTrue(store.tryCreate(request("short", "SHORT234"), null, BUDGET, Duration.ofSeconds(5)));
        store.putDecisionIfAbsent("short", decision(DeviceAuthorizationDecision.ALLOW), DECISION_TTL);

        clock.advance(Duration.ofSeconds(5));
        assertTrue(store.findByUserCode("SHORT234").isEmpty(), "решение не продлило срок");
    }

    @Test
    @DisplayName("Из параллельных захватов выдачу получает ровно один, и вместе с деталями")
    void parallelClaimHasSingleWinner() throws Exception {
        createLive();
        store.putDecisionIfAbsent(HASH, decision(DeviceAuthorizationDecision.ALLOW), DECISION_TTL);

        final var threads = 16;
        final var start = new CountDownLatch(1);
        final Callable<Boolean> claim = () -> {
            start.await();
            return store.takeAndRemove(HASH).filter(state -> DETAILS.equals(state.details())).isPresent();
        };

        final var executor = Executors.newFixedThreadPool(threads);

        try {
            final var futures = IntStream.range(0, threads).mapToObj(i -> executor.submit(claim)).toList();
            start.countDown();

            var winners = 0;
            for (final var future : futures) {
                winners += future.get() ? 1 : 0;
            }

            assertEquals(1, winners);
        } finally {
            executor.shutdownNow();
        }

        assertTrue(store.readAndConsumeAttempt(HASH, INTERVAL).isEmpty(), "проигравший получит expired_token");
    }

    @Test
    @DisplayName("Удалённая запись не принимает решение; повторное удаление безвредно")
    void removedRecordRejectsDecision() {
        createLive();
        store.remove(HASH);
        store.remove(HASH);

        assertTrue(store.putDecisionIfAbsent(HASH, decision(DeviceAuthorizationDecision.ALLOW), DECISION_TTL).isEmpty());
    }

    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
