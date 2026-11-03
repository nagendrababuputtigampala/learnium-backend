package com.learnium.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.learnium.model.user.UserAccount;
import com.learnium.repository.user.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AccountFlowServiceStudentTest {
    @Test
    void studentInviteSkipsBothNullAndBlankContactAddresses() {
        AccountTokenService tokens = mock(AccountTokenService.class);
        EmailOutboxService outbox = mock(EmailOutboxService.class);
        AccountFlowService service = new AccountFlowService(mock(UserAccountRepository.class), tokens, outbox,
                mock(PasswordEncoder.class), mock(RefreshTokenService.class));
        UserAccount student = new UserAccount();
        assertThat(service.sendStudentInvite(student)).isFalse();
        student.setEmail(" ");
        assertThat(service.sendStudentInvite(student)).isFalse();
        verifyNoInteractions(tokens, outbox);
    }
}
