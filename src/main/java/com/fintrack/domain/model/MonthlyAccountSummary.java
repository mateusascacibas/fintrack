package com.fintrack.domain.model;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;

/**
 * Resultado do relatório mensal — retornado por Account.getMonthlyReport().
 *
 * WHY Java Record?
 *   É um value object: carrega dados, sem identidade, sem comportamento.
 *   Record é perfeito: imutável, equals/hashCode automáticos, conciso.
 *
 * WHY no domínio e não em dto/?
 *   É o resultado de uma operação de domínio (Account.getMonthlyReport).
 *   O AccountResponse no layer de interfaces usa este para montar o JSON.
 */
public record MonthlyAccountSummary(
        YearMonth                month,
        BigDecimal               currentBalance,
        BigDecimal               totalDebits,
        BigDecimal               totalCredits,
        BigDecimal               netBalance,        // credits - debits
        Map<String, BigDecimal>  spendingByCategory
) {

    /** Retorna true se o mês teve mais créditos do que débitos. */
    public boolean isPositive() {
        return netBalance.compareTo(BigDecimal.ZERO) > 0;
    }

    /** Retorna a categoria com maior gasto, ou "Uncategorized" se vazio. */
    public String topSpendingCategory() {
        return spendingByCategory.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Uncategorized");
    }
}
