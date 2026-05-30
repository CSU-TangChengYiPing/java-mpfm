package com.mpfm.backend.application.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.infrastructure.persistence.entity.QosPolicyEntity;
import com.mpfm.backend.infrastructure.persistence.entity.UserEntity;
import com.mpfm.backend.infrastructure.persistence.repository.QosPolicyAuditRepository;
import com.mpfm.backend.infrastructure.persistence.repository.QosMountBindingRepository;
import com.mpfm.backend.infrastructure.persistence.repository.QosPolicyRepository;
import com.mpfm.backend.infrastructure.persistence.repository.QosProtocolBindingRepository;
import com.mpfm.backend.infrastructure.persistence.repository.UserRepository;
import com.mpfm.backend.infrastructure.persistence.repository.UserTransferGovernanceRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QosPolicyServiceTests {
    @Mock
    private QosPolicyRepository qosPolicyRepository;
    @Mock
    private QosPolicyAuditRepository qosPolicyAuditRepository;
    @Mock
    private QosMountBindingRepository qosMountBindingRepository;
    @Mock
    private QosProtocolBindingRepository qosProtocolBindingRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserTransferGovernanceRepository governanceRepository;
    @InjectMocks
    private QosPolicyService qosPolicyService;

    @Test
    void shouldBindUserToPolicy() {
        given(qosPolicyRepository.existsById(anyString())).willReturn(true);
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setQosProfile("default");
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(user));

        qosPolicyService.bindUserPolicy("alice", "default", "root");

        assertThat(user.getQosProfile()).isEqualTo("default");
        verify(userRepository).save(user);
    }

    @Test
    void shouldThrowWhenBindingUnknownPolicy() {
        given(qosPolicyRepository.existsById(anyString())).willReturn(true);
        given(qosPolicyRepository.existsById("unknown")).willReturn(false);
        assertThatThrownBy(() -> qosPolicyService.bindUserPolicy("alice", "unknown", "root"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldReturnEffectivePolicyByUserProfile() {
        UserEntity user = new UserEntity();
        user.setUsername("alice");
        user.setQosProfile("fast");
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(user));
        given(qosPolicyRepository.existsById(anyString())).willReturn(true);
        given(qosPolicyRepository.findById("fast")).willReturn(Optional.of(policy("fast", 32L * 1024 * 1024)));

        QosPolicyService.QosPolicy policy = qosPolicyService.effectivePolicy("alice");
        assertThat(policy.id()).isEqualTo("fast");
    }

    @Test
    void shouldResolveUserThenDefaultWhenNoCustomLimit() {
        UUID mountId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setUsername("alice");
        user.setQosProfile("user-policy");
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(user));
        given(qosPolicyRepository.existsById(anyString())).willReturn(true);
        given(qosPolicyRepository.findById("user-policy")).willReturn(Optional.of(policy("user-policy", 10)));
        assertThat(qosPolicyService.effectivePolicy("alice", mountId, "sftp").id()).isEqualTo("user-policy");

        user.setQosProfile("");
        given(qosPolicyRepository.findById("user")).willReturn(Optional.of(policy("user", 8)));
        assertThat(qosPolicyService.effectivePolicy("alice", mountId, null).id()).isEqualTo("user");
    }

    @Test
    void shouldListPolicies() {
        given(qosPolicyRepository.existsById(anyString())).willReturn(true);
        given(qosPolicyRepository.findAll()).willReturn(List.of(policy("default", 8L * 1024 * 1024)));
        List<QosPolicyService.QosPolicy> policies = qosPolicyService.listPolicies();
        assertThat(policies).hasSize(1);
        assertThat(policies.get(0).id()).isEqualTo("default");
    }

    private QosPolicyEntity policy(String id, long speed) {
        QosPolicyEntity entity = new QosPolicyEntity();
        entity.setId(id);
        entity.setName(id);
        entity.setMaxUploadBps(speed);
        entity.setMaxDownloadBps(speed);
        entity.setMaxConcurrentUploadTasks(1);
        entity.setMaxConcurrentDownloadTasks(1);
        entity.setEnabled(true);
        entity.setUpdatedBy("root");
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setUpdatedAt(OffsetDateTime.now());
        return entity;
    }
}
