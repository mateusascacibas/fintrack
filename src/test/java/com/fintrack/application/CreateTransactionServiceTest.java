package com.fintrack.application;

import com.fintrack.application.service.CreateTransactionService;
import com.fintrack.domain.exception.AccountNotFoundException;
import com.fintrack.domain.exception.InsufficientBalanceException;
import com.fintrack.domain.model.*;
import com.fintrack.domain.port.out.AccountRepository;
import com.fintrack.domain.port.out.EventPublisher;
import com.fintrack.domain.port.out.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * CreateTransactionServiceTest — Testa a camada de aplicação.
 *
 * @ExtendWith(MockitoExtension): Inicializa os mocks sem Spring context.
 * Muito mais rápido que @SpringBootTest.
 *
 * @Mock: Cria um mock (substituto) para as dependências.
 * @InjectMocks: Cria a instância real do serviço, injetando os mocks.
 *
 * OBJETIVO deste teste: verificar que o serviço orquestra corretamente
 * o domínio e os ports de saída. NÃO testa regras de negócio (isso é AccountTest).
 *
 * IMPORTANTE: ArgumentCaptor é uma ferramenta poderosa para verificar
 * COMO um método foi chamado, não apenas SE foi chamado.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CreateTransactionService")
class CreateTransactionServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private CreateTransactionService service;

    @Test
    @DisplayName("should complete debit transaction and update account balance")
    void shouldCompleteDebitTransaction() {
        // Arrange
        var accountId = UUID.randomUUID();
        var account = Account.create("Test", AccountType.CHECKING, new BigDecimal("500.00"));
        var debitAmount = new BigDecimal("200.00");

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        var result = service.execute(accountId, debitAmount, TransactionType.DEBIT,
            "Grocery shopping", "Food");

        // Assert
        assertThat(result.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(result.getAmount()).isEqualByComparingTo(debitAmount);

        // Verifica que o account foi salvo com saldo atualizado
        var accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getBalance()).isEqualByComparingTo("300.00");

        // Verifica que evento foi publicado
        verify(eventPublisher).publish(eq("transaction.created"), any());
    }

    @Test
    @DisplayName("should throw AccountNotFoundException when account does not exist")
    void shouldThrowWhenAccountNotFound() {
        var accountId = UUID.randomUUID();
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            service.execute(accountId, BigDecimal.TEN, TransactionType.DEBIT, "Test", "Test")
        ).isInstanceOf(AccountNotFoundException.class);

        // Nenhuma transação deve ser salva
        verifyNoInteractions(transactionRepository);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("should rethrow InsufficientBalanceException and not save transaction")
    void shouldRethrowInsufficientBalance() {
        var accountId = UUID.randomUUID();
        var account = Account.create("Test", AccountType.CHECKING, new BigDecimal("50.00"));

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        assertThatThrownBy(() ->
            service.execute(accountId, new BigDecimal("200.00"), TransactionType.DEBIT, "Test", "Test")
        ).isInstanceOf(InsufficientBalanceException.class);

        // Transação não deve ser persistida em caso de falha de domínio
        verifyNoInteractions(transactionRepository);
    }
}
