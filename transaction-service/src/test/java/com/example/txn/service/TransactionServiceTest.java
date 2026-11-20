package com.example.txn.service;

import com.example.txn.cache.CacheService;
import com.example.txn.messaging.TransactionEventPublisher;
import com.example.txn.repository.TransactionRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.txn.dto.CreateTransactionRequest;
import com.example.txn.dto.Decision;
import com.example.txn.dto.TransactionScoredEvent;
import com.example.txn.dto.TxDto;
import com.example.txn.dto.PrevTx;
import com.example.txn.dto.RuleResult;
import com.example.txn.dto.ScoringRequest;
import com.example.txn.dto.TransactionResponse;
import com.example.txn.dto.TransactionStatus;
import com.example.txn.dto.Tx;
import com.example.txn.exception.FraudUnavailableException;
import com.example.txn.exception.InvalidParameterException;
import com.example.txn.exception.NotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TransactionServiceTest {

    private static final Instant EVENT_TIME = Instant.parse("2024-01-01T10:00:00Z");

    private final TransactionRepository repository = mock(TransactionRepository.class);
    private final TransactionEventPublisher publisher = mock(TransactionEventPublisher.class);
    private final CacheService cache = mock(CacheService.class);
    private final TransactionService service = new TransactionService(repository, publisher, cache, 10);

    TransactionServiceTest() {
        // A cache that never hits: always run the loader, and ignore the cacheable predicate.
        when(cache.getOrLoad(anyString(), any(), any(), any(), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(3)).get());
    }

    private static CreateTransactionRequest request() {
        return new CreateTransactionRequest("ACC-1", new BigDecimal("50.00"), "USD", "M-1", "FOOD", "ID",
                EVENT_TIME);
    }

    private static TxDto txDto() {
        return new TxDto("tx-1", "ACC-1", new BigDecimal("50.00"), "USD", "M-1", "FOOD", "ID", EVENT_TIME);
    }

    private void accountExists() {
        when(repository.accountExists("ACC-1")).thenReturn(true);
        when(repository.findRecent(anyString(), any(), anyInt(), anyString())).thenReturn(List.of());
        when(repository.findPrevious(anyString(), anyString())).thenReturn(Optional.empty());
    }

    @Test
    void submitStoresReceivedRowPublishesAndReturnsReceived() {
        accountExists();

        TransactionResponse r = service.submit(request());

        assertThat(r.status()).isEqualTo(TransactionStatus.RECEIVED);
        assertThat(r.riskScore()).isNull();
        assertThat(r.ruleResults()).isNull();
        verify(repository).insert(any(Tx.class));
        verify(publisher).publishSubmitted(any(ScoringRequest.class));
        verify(repository, never()).updateDecision(anyString(), any(), anyInt(), any());
    }

    @Test
    void flaggedDecisionIsStoredAsFlagged() {
        List<RuleResult> rules = List.of(new RuleResult("HIGH_AMOUNT", true, 40, "big"));

        service.applyDecision(new TransactionScoredEvent(txDto(), new Decision(80, true, rules)));

        verify(repository).updateDecision("tx-1", TransactionStatus.FLAGGED, 80, rules);
        verify(cache).evict("txn:tx-1");
    }

    @Test
    void notFlaggedDecisionIsStoredAsApproved() {
        service.applyDecision(new TransactionScoredEvent(txDto(), new Decision(10, false, List.of())));

        verify(repository).updateDecision("tx-1", TransactionStatus.APPROVED, 10, List.of());
    }

    @Test
    void unknownAccountThrowsNotFoundAndDoesNotInsert() {
        when(repository.accountExists("ACC-1")).thenReturn(false);

        assertThatThrownBy(() -> service.submit(request())).isInstanceOf(NotFoundException.class);

        verify(repository, never()).insert(any());
        verify(publisher, never()).publishSubmitted(any());
    }

    @Test
    void publishFailureThrowsUnavailableAfterInsert() {
        accountExists();
        doThrow(new FraudUnavailableException("down")).when(publisher).publishSubmitted(any());

        assertThatThrownBy(() -> service.submit(request())).isInstanceOf(FraudUnavailableException.class);

        verify(repository).insert(any(Tx.class));
        verify(repository, never()).updateDecision(anyString(), any(), anyInt(), any());
    }

    @Test
    void historyAndPreviousTransactionArePublishedForScoring() {
        when(repository.accountExists("ACC-1")).thenReturn(true);
        Tx earlier = new Tx("old-1", "ACC-1", new BigDecimal("5.00"), "USD", "M-2", "FOOD", "SG",
                EVENT_TIME.minusSeconds(120));
        when(repository.findRecent(eq("ACC-1"), eq(EVENT_TIME), eq(10), anyString())).thenReturn(List.of(earlier));
        PrevTx prev = new PrevTx(EVENT_TIME.minusSeconds(120), "SG");
        when(repository.findPrevious(eq("ACC-1"), anyString())).thenReturn(Optional.of(prev));

        service.submit(request());

        ArgumentCaptor<ScoringRequest> captor = ArgumentCaptor.forClass(ScoringRequest.class);
        verify(publisher).publishSubmitted(captor.capture());
        ScoringRequest sent = captor.getValue();
        assertThat(sent.transaction().accountId()).isEqualTo("ACC-1");
        assertThat(sent.recentTransactions()).hasSize(1);
        assertThat(sent.recentTransactions().get(0).id()).isEqualTo("old-1");
        assertThat(sent.previousTransaction()).isEqualTo(prev);
    }

    @Test
    void getByIdThrowsNotFoundWhenMissing() {
        when(repository.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById("nope")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void listRejectsSizeOver100() {
        assertThatThrownBy(() -> service.list(null, null, 0, 101))
                .isInstanceOf(InvalidParameterException.class);
        verify(repository, never()).findPage(any(), any(), anyInt(), anyInt());
    }

    @Test
    void listRejectsNegativePage() {
        assertThatThrownBy(() -> service.list(null, null, -1, 20))
                .isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void listRejectsUnknownStatus() {
        assertThatThrownBy(() -> service.list(null, "BOGUS", 0, 20))
                .isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void listAppliesDefaultsWhenParamsMissing() {
        service.list(null, null, null, null);
        verify(repository).findPage(null, null, 0, 20);
    }
}
