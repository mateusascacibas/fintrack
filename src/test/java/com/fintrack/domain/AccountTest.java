package com.fintrack.domain;

import com.fintrack.domain.exception.DomainException;
import com.fintrack.domain.exception.InsufficientBalanceException;
import com.fintrack.domain.model.Account;
import com.fintrack.domain.model.AccountType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * AccountTest — Testes unitários PUROS do domínio.
 *
 * OBSERVE: Sem @SpringBootTest, sem mocks, sem contexto Spring.
 * O domínio é testado como Java puro. Isso é rápido (milissegundos) e confiável.
 *
 * WHY @Nested?
 * Agrupa testes relacionados. Facilita leitura e gera relatório hierárquico.
 * Estrutura: AccountTest > WhenCreating > shouldCreateWithPositiveBalance
 *
 * WHY @DisplayName?
 * Descreve o comportamento esperado em linguagem natural.
 * O relatório do JUnit fica legível para não-desenvolvedores.
 *
 * PADRÃO: Given-When-Then (ou Arrange-Act-Assert)
 * - Arrange: prepara o estado inicial
 * - Act: executa a ação sendo testada
 * - Assert: verifica o resultado esperado
 */
@DisplayName("Account domain model")
class AccountTest {

    @Nested
    @DisplayName("When creating an account")
    class WhenCreating {

        @Test
        @DisplayName("should create account with valid data")
        void shouldCreateAccountWithValidData() {
            // Arrange
            var name = "Main Checking";
            var type = AccountType.CHECKING;
            var initialBalance = new BigDecimal("1000.00");

            // Act
            var account = Account.create(name, type, initialBalance);

            // Assert
            assertThat(account.getId()).isNotNull();
            assertThat(account.getName()).isEqualTo(name);
            assertThat(account.getType()).isEqualTo(type);
            assertThat(account.getBalance()).isEqualByComparingTo(initialBalance);
            assertThat(account.isActive()).isTrue();
            assertThat(account.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("should reject negative initial balance")
        void shouldRejectNegativeInitialBalance() {
            // assertThatThrownBy: mais expressivo que try/catch em testes
            assertThatThrownBy(() ->
                Account.create("Test", AccountType.CHECKING, new BigDecimal("-1.00"))
            )
            .isInstanceOf(DomainException.class)
            .hasMessageContaining("negative");
        }
    }

    @Nested
    @DisplayName("When debiting")
    class WhenDebiting {

        @Test
        @DisplayName("should reduce balance by debit amount")
        void shouldReduceBalanceByDebitAmount() {
            var account = Account.create("Test", AccountType.CHECKING, new BigDecimal("500.00"));

            account.debit(new BigDecimal("200.00"));

            assertThat(account.getBalance()).isEqualByComparingTo("300.00");
        }

        @Test
        @DisplayName("should throw InsufficientBalanceException when balance is not enough")
        void shouldThrowWhenInsufficientBalance() {
            var account = Account.create("Test", AccountType.CHECKING, new BigDecimal("100.00"));

            assertThatThrownBy(() -> account.debit(new BigDecimal("150.00")))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("Insufficient balance");
        }

        @Test
        @DisplayName("should allow debiting exact balance amount")
        void shouldAllowDebitingExactBalance() {
            var account = Account.create("Test", AccountType.CHECKING, new BigDecimal("100.00"));

            account.debit(new BigDecimal("100.00"));

            assertThat(account.getBalance()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("should reject zero or negative debit amount")
        void shouldRejectZeroAmount() {
            var account = Account.create("Test", AccountType.CHECKING, new BigDecimal("100.00"));

            assertThatThrownBy(() -> account.debit(BigDecimal.ZERO))
                .isInstanceOf(DomainException.class);
        }
    }

    @Nested
    @DisplayName("When crediting")
    class WhenCrediting {

        @Test
        @DisplayName("should increase balance by credit amount")
        void shouldIncreaseBalance() {
            var account = Account.create("Test", AccountType.SAVINGS, new BigDecimal("100.00"));

            account.credit(new BigDecimal("50.00"));

            assertThat(account.getBalance()).isEqualByComparingTo("150.00");
        }
    }

    @Nested
    @DisplayName("When deactivating")
    class WhenDeactivating {

        @Test
        @DisplayName("should mark account as inactive")
        void shouldMarkAsInactive() {
            var account = Account.create("Test", AccountType.CHECKING, BigDecimal.ZERO);
            assertThat(account.isActive()).isTrue();

            account.deactivate();

            assertThat(account.isActive()).isFalse();
        }
    }
}
