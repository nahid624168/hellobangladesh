package com.exambase.backend.realtime

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Cross-instance fan-out. Single-node uses [InMemoryEventBus].
 * Multi-node: plug a Redis pub/sub implementation that publishes to the same channel names
 * (`contest:{id}`) and invokes local subscribers. Delivery remains at-least-once.
 */
fun interface EventBusSubscription {
    fun cancel()
}

interface EventBus {
    fun publish(channel: String, envelope: RealtimeEnvelope)
    fun subscribe(channel: String, handler: (RealtimeEnvelope) -> Unit): EventBusSubscription
}

class InMemoryEventBus : EventBus {
    private val handlers = ConcurrentHashMap<String, CopyOnWriteArrayList<(RealtimeEnvelope) -> Unit>>()

    override fun publish(channel: String, envelope: RealtimeEnvelope) {
        handlers[channel]?.forEach { h ->
            runCatching { h(envelope) }
        }
    }

    override fun subscribe(channel: String, handler: (RealtimeEnvelope) -> Unit): EventBusSubscription {
        val list = handlers.getOrPut(channel) { CopyOnWriteArrayList() }
        list.add(handler)
        return EventBusSubscription {
            list.remove(handler)
            if (list.isEmpty()) handlers.remove(channel, list)
        }
    }

    fun subscriberCount(channel: String): Int = handlers[channel]?.size ?: 0
}
