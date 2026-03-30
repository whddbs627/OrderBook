package com.orderbook.query.service

import com.orderbook.common.events.AccountId
import com.orderbook.common.events.BalanceSnapshot
import com.orderbook.common.events.Order
import com.orderbook.common.events.OrderId
import com.orderbook.common.events.OrderSide
import com.orderbook.common.events.OrderStatus
import com.orderbook.common.events.PositionSnapshot
import com.orderbook.common.events.ProjectionUpdate
import com.orderbook.common.events.Symbol
import com.orderbook.common.events.Trade
import com.orderbook.common.events.TradeId
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.Table
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.name
import org.jooq.impl.DSL.table
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant

@Service
class JooqProjectionStore(
    private val dsl: DSLContext
) : ProjectionStore {
    override fun apply(update: ProjectionUpdate) {
        dsl.transaction { configuration ->
            val ctx = org.jooq.impl.DSL.using(configuration)
            update.orders.forEach { order ->
                ctx.insertInto(ORDER_QUERY_VIEW)
                    .set(order.toAssignments())
                    .onConflict(ORDER_ID)
                    .doUpdate()
                    .set(order.toAssignments())
                    .where(ORDER_VERSION.le(order.version))
                    .execute()
            }
            update.balances.forEach { balance ->
                ctx.insertInto(BALANCE_QUERY_VIEW)
                    .set(
                        mapOf(
                            BALANCE_ACCOUNT_ID to balance.accountId.value,
                            BALANCE_CASH_BALANCE to balance.cashBalance,
                            BALANCE_UPDATED_AT to balance.updatedAt
                        )
                    )
                    .onConflict(BALANCE_ACCOUNT_ID)
                    .doUpdate()
                    .set(BALANCE_CASH_BALANCE, balance.cashBalance)
                    .set(BALANCE_UPDATED_AT, balance.updatedAt)
                    .where(BALANCE_UPDATED_AT.le(balance.updatedAt))
                    .execute()
            }
            update.positions.forEach { position ->
                ctx.insertInto(POSITION_QUERY_VIEW)
                    .set(
                        mapOf(
                            POSITION_ACCOUNT_ID to position.accountId.value,
                            POSITION_SYMBOL to position.symbol.value,
                            POSITION_QUANTITY to position.quantity
                        )
                    )
                    .onConflict(POSITION_ACCOUNT_ID, POSITION_SYMBOL)
                    .doUpdate()
                    .set(POSITION_QUANTITY, position.quantity)
                    .execute()
            }
            update.trades.forEach { trade ->
                ctx.insertInto(TRADE_QUERY_VIEW)
                    .set(
                        mapOf(
                            TRADE_ID to trade.tradeId.value,
                            TRADE_SYMBOL to trade.symbol.value,
                            BUY_ORDER_ID to trade.buyOrderId.value,
                            SELL_ORDER_ID to trade.sellOrderId.value,
                            BUY_ACCOUNT_ID to trade.buyAccountId.value,
                            SELL_ACCOUNT_ID to trade.sellAccountId.value,
                            TRADE_PRICE to trade.price,
                            TRADE_QUANTITY to trade.quantity,
                            TRADE_OCCURRED_AT to trade.occurredAt
                        )
                    )
                    .onConflict(TRADE_ID)
                    .doUpdate()
                    .set(TRADE_SYMBOL, trade.symbol.value)
                    .set(BUY_ORDER_ID, trade.buyOrderId.value)
                    .set(SELL_ORDER_ID, trade.sellOrderId.value)
                    .set(BUY_ACCOUNT_ID, trade.buyAccountId.value)
                    .set(SELL_ACCOUNT_ID, trade.sellAccountId.value)
                    .set(TRADE_PRICE, trade.price)
                    .set(TRADE_QUANTITY, trade.quantity)
                    .set(TRADE_OCCURRED_AT, trade.occurredAt)
                    .execute()
            }
        }
    }

    override fun reset() {
        dsl.transaction { configuration ->
            val ctx = org.jooq.impl.DSL.using(configuration)
            ctx.deleteFrom(TRADE_QUERY_VIEW).execute()
            ctx.deleteFrom(POSITION_QUERY_VIEW).execute()
            ctx.deleteFrom(BALANCE_QUERY_VIEW).execute()
            ctx.deleteFrom(ORDER_QUERY_VIEW).execute()
        }
    }

    override fun order(orderId: OrderId): Order? =
        dsl.selectFrom(ORDER_QUERY_VIEW)
            .where(ORDER_ID.eq(orderId.value))
            .fetchOne(::toOrder)

    override fun balances(accountId: AccountId): BalanceSnapshot? =
        dsl.selectFrom(BALANCE_QUERY_VIEW)
            .where(BALANCE_ACCOUNT_ID.eq(accountId.value))
            .fetchOne(::toBalance)

    override fun positions(accountId: AccountId): List<PositionSnapshot> =
        dsl.selectFrom(POSITION_QUERY_VIEW)
            .where(POSITION_ACCOUNT_ID.eq(accountId.value))
            .fetch(::toPosition)

    override fun trades(symbol: String?): List<Trade> {
        val query = dsl.selectFrom(TRADE_QUERY_VIEW)
        if (symbol != null) {
            query.where(TRADE_SYMBOL.eq(symbol))
        }
        return query
            .orderBy(TRADE_OCCURRED_AT.desc(), TRADE_ID.desc())
            .limit(500)
            .fetch(::toTrade)
    }

    private fun toOrder(record: Record): Order = Order(
        orderId = OrderId(record[ORDER_ID]!!),
        accountId = AccountId(record[ORDER_ACCOUNT_ID]!!),
        clientOrderId = com.orderbook.common.events.ClientOrderId(record[CLIENT_ORDER_ID]!!),
        symbol = Symbol(record[ORDER_SYMBOL]!!),
        side = OrderSide.valueOf(record[ORDER_SIDE]!!),
        price = record[ORDER_PRICE]!!,
        quantity = record[ORDER_QUANTITY]!!,
        filledQuantity = record[FILLED_QUANTITY]!!,
        status = OrderStatus.valueOf(record[ORDER_STATUS]!!),
        createdAt = record[ORDER_CREATED_AT]!!,
        updatedAt = record[ORDER_UPDATED_AT]!!,
        version = record[ORDER_VERSION]!!
    )

    private fun toBalance(record: Record): BalanceSnapshot = BalanceSnapshot(
        accountId = AccountId(record[BALANCE_ACCOUNT_ID]!!),
        cashBalance = record[BALANCE_CASH_BALANCE]!!,
        updatedAt = record[BALANCE_UPDATED_AT]!!
    )

    private fun toPosition(record: Record): PositionSnapshot = PositionSnapshot(
        accountId = AccountId(record[POSITION_ACCOUNT_ID]!!),
        symbol = Symbol(record[POSITION_SYMBOL]!!),
        quantity = record[POSITION_QUANTITY]!!
    )

    private fun toTrade(record: Record): Trade = Trade(
        tradeId = TradeId(record[TRADE_ID]!!),
        symbol = Symbol(record[TRADE_SYMBOL]!!),
        buyOrderId = OrderId(record[BUY_ORDER_ID]!!),
        sellOrderId = OrderId(record[SELL_ORDER_ID]!!),
        buyAccountId = AccountId(record[BUY_ACCOUNT_ID]!!),
        sellAccountId = AccountId(record[SELL_ACCOUNT_ID]!!),
        price = record[TRADE_PRICE]!!,
        quantity = record[TRADE_QUANTITY]!!,
        occurredAt = record[TRADE_OCCURRED_AT]!!
    )

    private fun Order.toAssignments(): Map<Field<*>, Any?> = mapOf(
        ORDER_ID to orderId.value,
        ORDER_ACCOUNT_ID to accountId.value,
        CLIENT_ORDER_ID to clientOrderId.value,
        ORDER_SYMBOL to symbol.value,
        ORDER_SIDE to side.name,
        ORDER_PRICE to price,
        ORDER_QUANTITY to quantity,
        FILLED_QUANTITY to filledQuantity,
        ORDER_STATUS to status.name,
        ORDER_CREATED_AT to createdAt,
        ORDER_UPDATED_AT to updatedAt,
        ORDER_VERSION to version
    )

    private companion object {
        val ORDER_QUERY_VIEW: Table<*> = table(name("order_query_view"))
        val ORDER_ID: Field<String> = field(name("order_id"), String::class.java)
        val ORDER_ACCOUNT_ID: Field<String> = field(name("account_id"), String::class.java)
        val CLIENT_ORDER_ID: Field<String> = field(name("client_order_id"), String::class.java)
        val ORDER_SYMBOL: Field<String> = field(name("symbol"), String::class.java)
        val ORDER_SIDE: Field<String> = field(name("side"), String::class.java)
        val ORDER_PRICE: Field<BigDecimal> = field(name("price"), BigDecimal::class.java)
        val ORDER_QUANTITY: Field<Long> = field(name("quantity"), Long::class.java)
        val FILLED_QUANTITY: Field<Long> = field(name("filled_quantity"), Long::class.java)
        val ORDER_STATUS: Field<String> = field(name("status"), String::class.java)
        val ORDER_CREATED_AT: Field<Instant> = field(name("created_at"), Instant::class.java)
        val ORDER_UPDATED_AT: Field<Instant> = field(name("updated_at"), Instant::class.java)
        val ORDER_VERSION: Field<Long> = field(name("version"), Long::class.java)

        val BALANCE_QUERY_VIEW: Table<*> = table(name("balance_query_view"))
        val BALANCE_ACCOUNT_ID: Field<String> = field(name("account_id"), String::class.java)
        val BALANCE_CASH_BALANCE: Field<BigDecimal> = field(name("cash_balance"), BigDecimal::class.java)
        val BALANCE_UPDATED_AT: Field<Instant> = field(name("updated_at"), Instant::class.java)

        val POSITION_QUERY_VIEW: Table<*> = table(name("position_query_view"))
        val POSITION_ACCOUNT_ID: Field<String> = field(name("account_id"), String::class.java)
        val POSITION_SYMBOL: Field<String> = field(name("symbol"), String::class.java)
        val POSITION_QUANTITY: Field<Long> = field(name("quantity"), Long::class.java)

        val TRADE_QUERY_VIEW: Table<*> = table(name("trade_query_view"))
        val TRADE_ID: Field<String> = field(name("trade_id"), String::class.java)
        val TRADE_SYMBOL: Field<String> = field(name("symbol"), String::class.java)
        val BUY_ORDER_ID: Field<String> = field(name("buy_order_id"), String::class.java)
        val SELL_ORDER_ID: Field<String> = field(name("sell_order_id"), String::class.java)
        val BUY_ACCOUNT_ID: Field<String> = field(name("buy_account_id"), String::class.java)
        val SELL_ACCOUNT_ID: Field<String> = field(name("sell_account_id"), String::class.java)
        val TRADE_PRICE: Field<BigDecimal> = field(name("price"), BigDecimal::class.java)
        val TRADE_QUANTITY: Field<Long> = field(name("quantity"), Long::class.java)
        val TRADE_OCCURRED_AT: Field<Instant> = field(name("occurred_at"), Instant::class.java)
    }
}
