package com.mpfm.backend.application.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TransferBandwidthLimiterTests {

    @Test
    void shouldRejectUploadWhenBurstExceedsPerSecondRate() {
        QosPolicyService qosPolicyService = mock(QosPolicyService.class);
        when(qosPolicyService.effectivePolicy("u1"))
                .thenReturn(new QosPolicyService.QosPolicy("p1", "P1", 100L, 100L, 1, 1, true, "root"));
        AtomicLong now = new AtomicLong(0L);
        TransferBandwidthLimiter limiter = new TransferBandwidthLimiter(qosPolicyService, now::get);

        limiter.checkUpload("u1", 100L);
        BusinessException exception = assertThrows(BusinessException.class, () -> limiter.checkUpload("u1", 100L));

        assertEquals(ErrorCode.CAPABILITY_RESTRICTED, exception.getCode());
    }

    @Test
    void shouldKeepDifferentUsersIndependent() {
        QosPolicyService qosPolicyService = mock(QosPolicyService.class);
        when(qosPolicyService.effectivePolicy("u1"))
                .thenReturn(new QosPolicyService.QosPolicy("p1", "P1", 100L, 100L, 1, 1, true, "root"));
        when(qosPolicyService.effectivePolicy("u2"))
                .thenReturn(new QosPolicyService.QosPolicy("p2", "P2", 100L, 100L, 1, 1, true, "root"));
        AtomicLong now = new AtomicLong(0L);
        TransferBandwidthLimiter limiter = new TransferBandwidthLimiter(qosPolicyService, now::get);

        assertDoesNotThrow(() -> limiter.checkUpload("u1", 100L));
        assertDoesNotThrow(() -> limiter.checkUpload("u2", 100L));
    }

    @Test
    void shouldKeepUploadAndDownloadIndependent() {
        QosPolicyService qosPolicyService = mock(QosPolicyService.class);
        when(qosPolicyService.effectivePolicy("u1"))
                .thenReturn(new QosPolicyService.QosPolicy("p1", "P1", 100L, 100L, 1, 1, true, "root"));
        AtomicLong now = new AtomicLong(0L);
        TransferBandwidthLimiter limiter = new TransferBandwidthLimiter(qosPolicyService, now::get);

        assertDoesNotThrow(() -> limiter.checkUpload("u1", 100L));
        assertDoesNotThrow(() -> limiter.checkDownload("u1", 100L));
    }

    @Test
    void shouldIgnoreZeroBytes() {
        QosPolicyService qosPolicyService = mock(QosPolicyService.class);
        when(qosPolicyService.effectivePolicy("u1"))
                .thenReturn(new QosPolicyService.QosPolicy("p1", "P1", 100L, 100L, 1, 1, true, "root"));
        AtomicLong now = new AtomicLong(0L);
        TransferBandwidthLimiter limiter = new TransferBandwidthLimiter(qosPolicyService, now::get);

        limiter.checkUpload("u1", 0L);
    }

    @Test
    void shouldAwaitUploadPermitInsteadOfThrowingWhenBudgetExceeded() {
        QosPolicyService qosPolicyService = mock(QosPolicyService.class);
        when(qosPolicyService.effectivePolicy("u1"))
                .thenReturn(new QosPolicyService.QosPolicy("p1", "P1", 100L, 100L, 1, 1, true, "root"));
        AtomicLong now = new AtomicLong(0L);
        AtomicReference<Long> parkedNanos = new AtomicReference<>(0L);
        TransferBandwidthLimiter limiter = new TransferBandwidthLimiter(
                qosPolicyService,
                now::get,
                nanos -> {
                    parkedNanos.set(nanos);
                    now.addAndGet(nanos);
                });

        limiter.awaitUploadPermit("u1", 100L);
        assertDoesNotThrow(() -> limiter.awaitUploadPermit("u1", 100L));
        assertEquals(1_000_000_000L, parkedNanos.get());
    }

    @Test
    void shouldExposeUploadRateSnapshotFromLimiterSamples() {
        QosPolicyService qosPolicyService = mock(QosPolicyService.class);
        when(qosPolicyService.effectivePolicy("u1"))
                .thenReturn(new QosPolicyService.QosPolicy("p1", "P1", 1024L, 1024L, 1, 1, true, "root"));
        AtomicLong now = new AtomicLong(0L);
        TransferBandwidthLimiter limiter = new TransferBandwidthLimiter(qosPolicyService, now::get, now::addAndGet);

        limiter.awaitUploadPermit("u1", 1024L);

        assertTrue(limiter.currentUploadBps("u1") > 0L);
        assertTrue(limiter.observedUploadUsers().contains("u1"));
    }
}
