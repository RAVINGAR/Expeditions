package com.ravingarinc.expeditions.api

import kotlin.random.Random

class WeightedCollection<E> : Collection<E> {
    private val orderedEntries: MutableList<Entry<E>> = ArrayList()
    private var accumulatedWeight: Double = 0.0
    override val size: Int
        get() = orderedEntries.size

    override fun isEmpty(): Boolean {
        return orderedEntries.isEmpty()
    }

    override fun iterator(): Iterator<E> {
        return object : Iterator<E> {
            private var i = 0

            override fun hasNext(): Boolean {
                return i < orderedEntries.size
            }

            override fun next(): E {
                return orderedEntries[i++].value
            }
        }
    }

    override fun containsAll(elements: Collection<E>): Boolean {
        throw UnsupportedOperationException()
    }

    override fun contains(element: E): Boolean {
        throw UnsupportedOperationException()
    }

    fun add(element: E, weight: Double) {
        accumulatedWeight += weight
        val entry: Entry<E> = Entry(size, accumulatedWeight, element)
        orderedEntries.add(entry)
    }

    fun random(): E {
        if (size == 1) {
            return orderedEntries[0].value
        }
        val r = Random.nextDouble() * accumulatedWeight
        for (entry in orderedEntries) {
            if (entry.accumulatedWeight >= r) {
                return entry.value
            }
        }
        throw IllegalStateException("WeightedCollection was empty!")
    }

    private class Entry<E>(val index: Int, val accumulatedWeight: Double, val value: E)
}