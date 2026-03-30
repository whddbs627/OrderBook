package com.orderbook.ledger.kafka

import com.orderbook.common.events.BalanceUpdated
import com.orderbook.common.events.LedgerAppended
import com.orderbook.common.events.LedgerEntryCommand
import com.orderbook.common.events.LedgerEntryType
import com.orderbook.common.events.Symbol
import com.orderbook.common.events.TradeExecuted
import com.orderbook.common.kafka.DomainEventPublisher
import com.orderbook.common.kafka.EventEnvelopeMapper
import com.orderbook.common.kafka.KafkaTopics
import com.orderbook.ledger.repository.EventStoreRepository
import com.orderbook.ledger.service.LedgerService
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Component
class LedgerEventsConsumer(
    private val ledgerService: LedgerService,
    private val eventPublisher: DomainEventPublisher,
    private val envelopeMapper: EventEnvelopeMapper,
    private val eventStoreRepository: EventStoreRepository,
    private val metrics: LedgerMetrics
) {
    @Transactional
    @KafkaListener(topics = [KafkaTopics.TRADES_EXECUTED])
    fun onTradeExecuted(message: String) {
        metrics.incrementConsumed(KafkaTopics.TRADES_EXECUTED)
        val event = envelopeMapper.deserialize(message) as? TradeExecuted ?: return
        val result = ledgerService.append(listOf(event.trade), event.traceId)
        if (result.balances.isEmpty() && result.positions.isEmpty()) {
            return
        }
        val ledgerAppended = LedgerAppended(
            aggregateId = event.trade.tradeId.value,
            symbol = event.trade.symbol,
            traceId = event.traceId,
            entries = entriesFor(event)
        )
        val balanceEvents = result.balances.map { balance ->
            BalanceUpdated(
                aggregateId = balance.accountId.value,
                accountId = balance.accountId,
                traceId = event.traceId,
                balance = balance,
                positions = result.positions.filter { it.accountId == balance.accountId }
            )
        }
        eventStoreRepository.append(listOf(ledgerAppended) + balanceEvents)
        eventPublisher.publish(KafkaTopics.LEDGER_APPENDED, ledgerAppended.aggregateId, ledgerAppended)
        metrics.incrementProduced(KafkaTopics.LEDGER_APPENDED)

        balanceEvents.forEach { balanceUpdated ->
            eventPublisher.publish(KafkaTopics.BALANCES_UPDATED, balanceUpdated.aggregateId, balanceUpdated)
            metrics.incrementProduced(KafkaTopics.BALANCES_UPDATED)
        }
    }

    private fun entriesFor(event: TradeExecuted): List<LedgerEntryCommand> {
        val trade = event.trade
        val notional = trade.price.multiply(BigDecimal.valueOf(trade.quantity))
        return listOf(
            LedgerEntryCommand(trade.buyAccountId, null, LedgerEntryType.CASH_DEBIT, notional.negate(), 0L, trade.tradeId.value, trade.occurredAt),
            LedgerEntryCommand(trade.sellAccountId, null, LedgerEntryType.CASH_CREDIT, notional, 0L, trade.tradeId.value, trade.occurredAt),
            LedgerEntryCommand(trade.buyAccountId, Symbol(trade.symbol.value), LedgerEntryType.POSITION_CREDIT, BigDecimal.ZERO, trade.quantity, trade.tradeId.value, trade.occurredAt),
            LedgerEntryCommand(trade.sellAccountId, Symbol(trade.symbol.value), LedgerEntryType.POSITION_DEBIT, BigDecimal.ZERO, -trade.quantity, trade.tradeId.value, trade.occurredAt)
        )
    }
}
