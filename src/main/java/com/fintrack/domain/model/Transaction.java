package com.fintrack.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain model — Transaction.
 *
 * WHY a Java Record?
 *   Records (Java 16+) são imutáveis por design: todos os campos são final,
 *   equals/hashCode/toString são gerados automaticamente baseados nos componentes.
 *   Para um evento financeiro — que nunca deve ser alterado após criado —
 *   Record é a escolha semanticamente correta.
 *
 * CONCEITOS APLICADOS:
 *   Dia 1 — Stack/Heap: cada Transaction criada é um objeto no Heap.
 *            Após sair de escopo sem referência, elegível para GC.
 *   Dia 3 — Reference Types: Transaction em cache → SoftReference (cara de recriar).
 *            Transaction como chave de mapa → nunca WeakReference (valor referencia chave).
 *   Dia 5 — Stream-friendly: todos os métodos são predicados puros — sem efeito colateral,
 *            ideais para .filter(), .anyMatch(), .collect().
 */
public record Transaction(

        UUID              id,           // identidade — imutável, gerado uma vez
        UUID              accountId,    // referência à conta dona desta transação
        BigDecimal        amount,       // valor — sempre positivo (validado no constructor)
        TransactionType   type,         // DEBIT ou CREDIT
        TransactionStatus status,       // PENDING, COMPLETED, FAILED
        String            description,  // descrição livre ("Supermercado Pão de Açúcar")
        String            category,     // categoria ("Alimentação", "Transporte"...)
        LocalDateTime     occurredAt,   // quando aconteceu (imutável — fato histórico)
        LocalDateTime     processedAt   // quando foi processado pelo sistema

) {

    // ─────────────────────────────────────────────────────────────────────────
    // Compact Constructor — validação centralizada no domínio
    //
    // WHY compact constructor?
    //   Em Records, o compact constructor roda antes da atribuição dos campos.
    //   É o único lugar para validar — sem precisar de método validate() separado.
    //   Garante que um Transaction inválido NUNCA existe — invariante do domínio.
    // ─────────────────────────────────────────────────────────────────────────
    public Transaction {
        Objects.requireNonNull(id,          "Transaction id cannot be null");
        Objects.requireNonNull(accountId,   "Account id cannot be null");
        Objects.requireNonNull(amount,      "Amount cannot be null");
        Objects.requireNonNull(type,        "Transaction type cannot be null");
        Objects.requireNonNull(status,      "Transaction status cannot be null");
        Objects.requireNonNull(occurredAt,  "OccurredAt cannot be null");

        // Regra de domínio: valor deve ser positivo
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Transaction amount must be positive, got: " + amount
            );
        }

        // Regra de domínio: descrição não pode ser vazia
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Description cannot be blank");
        }

        // Normaliza categoria: null → "Uncategorized"
        // WHY aqui e não no chamador?
        //   Invariante do domínio: toda Transaction TEM uma categoria.
        //   Se o chamador não informar, o domínio decide o padrão.
        category = (category == null || category.isBlank()) ? "Uncategorized" : category.trim();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Factory Methods — Effective Java Item 1
    //
    // WHY factory em vez de new Transaction(...) direto?
    //   1. Nome descritivo: Transaction.createDebit() é mais legível que new Transaction(...)
    //   2. Pode retornar um subtipo no futuro (ex: RecurringTransaction)
    //   3. Encapsula a geração de UUID e timestamps — chamador não se preocupa
    // ─────────────────────────────────────────────────────────────────────────

    /** Cria uma transação de DÉBITO imediatamente confirmada. */
    public static Transaction createDebit(
            UUID accountId, BigDecimal amount,
            String description, String category) {

        LocalDateTime now = LocalDateTime.now();
        return new Transaction(
                UUID.randomUUID(), accountId, amount,
                TransactionType.DEBIT, TransactionStatus.COMPLETED,
                description, category, now, now
        );
    }

    /** Cria uma transação de CRÉDITO imediatamente confirmada. */
    public static Transaction createCredit(
            UUID accountId, BigDecimal amount,
            String description, String category) {

        LocalDateTime now = LocalDateTime.now();
        return new Transaction(
                UUID.randomUUID(), accountId, amount,
                TransactionType.CREDIT, TransactionStatus.COMPLETED,
                description, category, now, now
        );
    }

    /** Cria uma transação com status PENDING (ex: aguardando autorização). */
    public static Transaction createPending(
            UUID accountId, BigDecimal amount,
            TransactionType type, String description, String category) {

        return new Transaction(
                UUID.randomUUID(), accountId, amount,
                type, TransactionStatus.PENDING,
                description, category, LocalDateTime.now(), null
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Métodos de negócio — predicados puros para uso em Streams
    //
    // WHY métodos no Record e não lógica inline nos Streams?
    //   Encapsulamento: "o que é uma transação de alto valor" pertence ao domínio,
    //   não ao código que consome o domínio.
    //
    //   Uso típico (Dia 5 — Short-Circuit):
    //     transactions.stream()
    //         .anyMatch(Transaction::isHighValue)   ← short-circuit
    //         .filter(Transaction::isCompleted)     ← lazy
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Retorna true se o valor supera o limiar de risco configurado.
     *
     * Uso em Stream (Dia 5 — Short-Circuit):
     *   transactions.stream().anyMatch(tx -> tx.isHighValue(threshold))
     *   Para no PRIMEIRO match — não precisa varrer a lista toda.
     */
    public boolean isHighValue(BigDecimal threshold) {
        return amount.compareTo(threshold) > 0;
    }

    /** Predicado para uso em .filter() — operação stateless (Dia 5). */
    public boolean isDebit()     { return type   == TransactionType.DEBIT; }
    public boolean isCredit()    { return type   == TransactionType.CREDIT; }
    public boolean isCompleted() { return status == TransactionStatus.COMPLETED; }
    public boolean isPending()   { return status == TransactionStatus.PENDING; }
    public boolean isFailed()    { return status == TransactionStatus.FAILED; }

    /**
     * Verifica se a transação pertence ao mês/ano informado.
     *
     * Uso típico (Dia 5 — Lazy + Short-Circuit):
     *   transactions.stream()
     *       .filter(tx -> tx.belongsTo(YearMonth.of(2026, 7)))  // lazy
     *       .collect(Collectors.toList())
     */
    public boolean belongsTo(YearMonth month) {
        return YearMonth.from(occurredAt).equals(month);
    }

    /**
     * Retorna uma cópia desta transação com status COMPLETED.
     *
     * WHY retornar nova instância em vez de mutar?
     *   Records são imutáveis. Para "mudar" um campo, cria-se nova instância.
     *   Imutabilidade garante thread safety sem sincronização (Dia 4).
     */
    public Transaction complete() {
        return new Transaction(
                id, accountId, amount, type, TransactionStatus.COMPLETED,
                description, category, occurredAt, LocalDateTime.now()
        );
    }

    /** Retorna uma cópia com status FAILED. */
    public Transaction fail() {
        return new Transaction(
                id, accountId, amount, type, TransactionStatus.FAILED,
                description, category, occurredAt, LocalDateTime.now()
        );
    }

    // toString compacto para logs — o Record já gera um, mas este é mais legível
    @Override
    public String toString() {
        return "Transaction{id=%s, %s %s, status=%s, category='%s'}"
                .formatted(id, type, amount, status, category);
    }
}
