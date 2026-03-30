package com.orderbook.ledger.service

import com.orderbook.common.events.AccountId
import com.orderbook.common.events.BalanceSnapshot
import com.orderbook.common.events.LedgerAppendResult
import com.orderbook.common.events.LedgerEntryType
import com.orderbook.common.events.PositionSnapshot
import com.orderbook.common.events.Symbol
import com.orderbook.common.events.Trade
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.Table
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.name
import org.jooq.impl.DSL.table
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant

@Service
class JooqLedgerService(
    private val dsl: DSLContext
) : LedgerService {
    @Transactional
    override fun append(trades: List<Trade>, traceId: String): LedgerAppendResult {
        if (trades.isEmpty()) {
            return LedgerAppendResult(emptyList(), emptyList())
        }

        val touchedAccounts = linkedSetOf<AccountId>()
        val touchedPositions = linkedSetOf<Pair<AccountId, Symbol>>()

        trades.forEach { trade ->
            if (!markTradeProcessed(dsl, trade.tradeId.value, traceId, trade.occurredAt)) {
                return@forEach
            }

            val notional = trade.price.multiply(BigDecimal.valueOf(trade.quantity))
            val balanceUpdatedAt = Instant.now()
            val positionUpdatedAt = Instant.now()

            insertLedgerEntry(
                ctx = dsl,
                tradeId = trade.tradeId.value,
                accountId = trade.buyAccountId.value,
                symbol = trade.symbol.value,
                entryType = LedgerEntryType.CASH_DEBIT,
                amount = notional.negate(),
                quantity = 0L,
                occurredAt = trade.occurredAt,
                traceId = traceId
            )
            insertLedgerEntry(
                ctx = dsl,
                tradeId = trade.tradeId.value,
                accountId = trade.sellAccountId.value,
                symbol = trade.symbol.value,
                entryType = LedgerEntryType.CASH_CREDIT,
                amount = notional,
                quantity = 0L,
                occurredAt = trade.occurredAt,
                traceId = traceId
            )
            insertLedgerEntry(
                ctx = dsl,
                tradeId = trade.tradeId.value,
                accountId = trade.buyAccountId.value,
                symbol = trade.symbol.value,
                entryType = LedgerEntryType.POSITION_CREDIT,
                amount = BigDecimal.ZERO,
                quantity = trade.quantity,
                occurredAt = trade.occurredAt,
                traceId = traceId
            )
            insertLedgerEntry(
                ctx = dsl,
                tradeId = trade.tradeId.value,
                accountId = trade.sellAccountId.value,
                symbol = trade.symbol.value,
                entryType = LedgerEntryType.POSITION_DEBIT,
                amount = BigDecimal.ZERO,
                quantity = -trade.quantity,
                occurredAt = trade.occurredAt,
                traceId = traceId
            )

            applyBalanceDelta(dsl, trade.buyAccountId.value, notional.negate(), balanceUpdatedAt)
            applyBalanceDelta(dsl, trade.sellAccountId.value, notional, balanceUpdatedAt)
            applyPositionDelta(dsl, trade.buyAccountId.value, trade.symbol.value, trade.quantity, positionUpdatedAt)
            applyPositionDelta(dsl, trade.sellAccountId.value, trade.symbol.value, -trade.quantity, positionUpdatedAt)

            touchedAccounts += trade.buyAccountId
            touchedAccounts += trade.sellAccountId
            touchedPositions += trade.buyAccountId to trade.symbol
            touchedPositions += trade.sellAccountId to trade.symbol
        }

        val balances = touchedAccounts.map(::balance)
        val positions = touchedPositions.mapNotNull { (accountId, symbol) -> position(accountId, symbol) }
        return LedgerAppendResult(balances, positions)
    }

    private fun markTradeProcessed(
        ctx: DSLContext,
        tradeId: String,
        traceId: String,
        occurredAt: Instant
    ): Boolean =
        ctx.insertInto(PROCESSED_TRADES)
            .set(
                mapOf(
                    PROCESSED_TRADE_ID to tradeId,
                    PROCESSED_TRACE_ID to traceId,
                    PROCESSED_OCCURRED_AT to occurredAt,
                    PROCESSED_PROCESSED_AT to Instant.now()
                )
            )
            .onConflict(PROCESSED_TRADE_ID)
            .doNothing()
            .execute() > 0

    override fun balance(accountId: AccountId): BalanceSnapshot =
        dsl.selectFrom(BALANCES)
            .where(BALANCE_ACCOUNT_ID.eq(accountId.value))
            .fetchOne(::toBalance)
            ?: BalanceSnapshot(accountId, BigDecimal.ZERO, Instant.now())

    override fun positions(accountId: AccountId): List<PositionSnapshot> =
        dsl.selectFrom(POSITIONS)
            .where(POSITION_ACCOUNT_ID.eq(accountId.value))
            .and(POSITION_QUANTITY.ne(0L))
            .fetch(::toPosition)

    private fun position(accountId: AccountId, symbol: Symbol): PositionSnapshot? =
        dsl.selectFrom(POSITIONS)
            .where(POSITION_ACCOUNT_ID.eq(accountId.value))
            .and(POSITION_SYMBOL.eq(symbol.value))
            .fetchOne(::toPosition)

    private fun insertLedgerEntry(
        ctx: DSLContext,
        tradeId: String,
        accountId: String,
        symbol: String,
        entryType: LedgerEntryType,
        amount: BigDecimal,
        quantity: Long,
        occurredAt: Instant,
        traceId: String
    ) {
        ctx.insertInto(LEDGER_ENTRIES)
            .set(
                mapOf(
                    LEDGER_TRADE_ID to tradeId,
                    LEDGER_ACCOUNT_ID to accountId,
                    LEDGER_SYMBOL to symbol,
                    LEDGER_ENTRY_TYPE to entryType.name,
                    LEDGER_AMOUNT to amount,
                    LEDGER_QUANTITY to quantity,
                    LEDGER_REFERENCE_ID to tradeId,
                    LEDGER_OCCURRED_AT to occurredAt,
                    LEDGER_TRACE_ID to traceId
                )
            )
            .execute()
    }

    private fun applyBalanceDelta(ctx: DSLContext, accountId: String, delta: BigDecimal, updatedAt: Instant) {
        ctx.insertInto(BALANCES)
            .set(
                mapOf(
                    BALANCE_ACCOUNT_ID to accountId,
                    CASH_BALANCE to delta,
                    BALANCE_UPDATED_AT to updatedAt
                )
            )
            .onConflict(BALANCE_ACCOUNT_ID)
            .doUpdate()
            .set(CASH_BALANCE, CASH_BALANCE.plus(delta))
            .set(BALANCE_UPDATED_AT, updatedAt)
            .execute()
    }

    private fun applyPositionDelta(ctx: DSLContext, accountId: String, symbol: String, delta: Long, updatedAt: Instant) {
        ctx.insertInto(POSITIONS)
            .set(
                mapOf(
                    POSITION_ACCOUNT_ID to accountId,
                    POSITION_SYMBOL to symbol,
                    POSITION_QUANTITY to delta,
                    POSITION_UPDATED_AT to updatedAt
                )
            )
            .onConflict(POSITION_ACCOUNT_ID, POSITION_SYMBOL)
            .doUpdate()
            .set(POSITION_QUANTITY, POSITION_QUANTITY.plus(delta))
            .set(POSITION_UPDATED_AT, updatedAt)
            .execute()
    }

    private fun toBalance(record: Record): BalanceSnapshot = BalanceSnapshot(
        accountId = AccountId(record[BALANCE_ACCOUNT_ID]!!),
        cashBalance = record[CASH_BALANCE]!!,
        updatedAt = record[BALANCE_UPDATED_AT]!!
    )

    private fun toPosition(record: Record): PositionSnapshot = PositionSnapshot(
        accountId = AccountId(record[POSITION_ACCOUNT_ID]!!),
        symbol = Symbol(record[POSITION_SYMBOL]!!),
        quantity = record[POSITION_QUANTITY]!!
    )

    private companion object {
        val LEDGER_ENTRIES: Table<*> = table(name("ledger_entries"))
        val LEDGER_TRADE_ID: Field<String> = field(name("trade_id"), String::class.java)
        val LEDGER_ACCOUNT_ID: Field<String> = field(name("account_id"), String::class.java)
        val LEDGER_SYMBOL: Field<String> = field(name("symbol"), String::class.java)
        val LEDGER_ENTRY_TYPE: Field<String> = field(name("entry_type"), String::class.java)
        val LEDGER_AMOUNT: Field<BigDecimal> = field(name("amount"), BigDecimal::class.java)
        val LEDGER_QUANTITY: Field<Long> = field(name("quantity"), Long::class.java)
        val LEDGER_REFERENCE_ID: Field<String> = field(name("reference_id"), String::class.java)
        val LEDGER_OCCURRED_AT: Field<Instant> = field(name("occurred_at"), Instant::class.java)
        val LEDGER_TRACE_ID: Field<String> = field(name("trace_id"), String::class.java)

        val BALANCES: Table<*> = table(name("balances"))
        val BALANCE_ACCOUNT_ID: Field<String> = field(name("account_id"), String::class.java)
        val CASH_BALANCE: Field<BigDecimal> = field(name("cash_balance"), BigDecimal::class.java)
        val BALANCE_UPDATED_AT: Field<Instant> = field(name("updated_at"), Instant::class.java)

        val POSITIONS: Table<*> = table(name("positions"))
        val POSITION_ACCOUNT_ID: Field<String> = field(name("account_id"), String::class.java)
        val POSITION_SYMBOL: Field<String> = field(name("symbol"), String::class.java)
        val POSITION_QUANTITY: Field<Long> = field(name("quantity"), Long::class.java)
        val POSITION_UPDATED_AT: Field<Instant> = field(name("updated_at"), Instant::class.java)

        val PROCESSED_TRADES: Table<*> = table(name("processed_trades"))
        val PROCESSED_TRADE_ID: Field<String> = field(name("trade_id"), String::class.java)
        val PROCESSED_TRACE_ID: Field<String> = field(name("trace_id"), String::class.java)
        val PROCESSED_OCCURRED_AT: Field<Instant> = field(name("occurred_at"), Instant::class.java)
        val PROCESSED_PROCESSED_AT: Field<Instant> = field(name("processed_at"), Instant::class.java)
    }
}
