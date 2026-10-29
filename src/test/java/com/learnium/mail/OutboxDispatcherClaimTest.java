package com.learnium.mail;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.learnium.repository.auth.EmailOutboxRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

/**
 * The dispatcher's claim protocol, isolated: a candidate whose atomic
 * claim is lost to another dispatcher instance must not be delivered, and
 * a claimed row that vanished before delivery is skipped quietly.
 */
class OutboxDispatcherClaimTest {

    private final EmailOutboxRepository repository = mock(EmailOutboxRepository.class);
    private final MailTemplateRenderer renderer = mock(MailTemplateRenderer.class);
    private final EmailSender emailSender = mock(EmailSender.class);
    private final PayloadCipher payloadCipher = mock(PayloadCipher.class);
    private final PlatformTransactionManager transactionManager =
            mock(PlatformTransactionManager.class);

    private final OutboxDispatcher dispatcher =
            new OutboxDispatcher(
                    repository,
                    renderer,
                    emailSender,
                    payloadCipher,
                    "http://localhost:5173",
                    transactionManager);

    private final UUID rowId = UUID.randomUUID();

    private void stubCandidatesAndClaim(int claimResult) {
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(repository.findDispatchCandidateIds(anyInt(), any(), any()))
                .thenReturn(List.of(rowId));
        when(repository.claimForSending(eq(rowId), anyInt(), any(), any())).thenReturn(claimResult);
    }

    @Test
    void candidateWhoseClaimIsLostToAnotherInstanceIsNotDelivered() {
        stubCandidatesAndClaim(0);

        dispatcher.dispatchPending();

        verify(repository, never()).findById(any());
        verify(emailSender, never()).send(any());
    }

    @Test
    void claimedRowThatVanishedBeforeDeliveryIsSkipped() {
        stubCandidatesAndClaim(1);
        when(repository.findById(rowId)).thenReturn(Optional.empty());

        dispatcher.dispatchPending();

        verify(emailSender, never()).send(any());
    }
}
