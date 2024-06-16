package com.ravingarinc.expeditions.api

import java.util.concurrent.ThreadLocalRandom

class WeightedCollection<E> : Collection<E> {
    private val entries: MutableMap<E, Entry<E>> = HashMap()
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

    fun getTotalWeight() : Double {
        return accumulatedWeight
    }

    override fun containsAll(elements: Collection<E>): Boolean {
        throw UnsupportedOperationException()
    }

    override fun contains(element: E): Boolean {
        throw UnsupportedOperationException()
    }

    fun add(element: E, weight: Double) {
        accumulatedWeight += weight
        val entry: Entry<E> = Entry(size, accumulatedWeight, weight, element)
        entries[entry.value] = entry
        orderedEntries.add(entry)
    }

    fun remove(element: E) : Boolean {
        entries.remove(element)?.let {
            orderedEntries.removeAt(it.index)
            accumulatedWeight -= it.originWeight
            for(i in it.index until orderedEntries.size) {
                val o = orderedEntries[i]
                o.index = o.index - 1
                o.accumulatedWeight -= it.originWeight
            }
            return true
        }
        return false
    }

    fun random(): E {
        if (size == 1) {
            return orderedEntries[0].value
        }
        val r = ThreadLocalRandom.current().nextDouble() * accumulatedWeight
        for (entry in orderedEntries) {
            if (entry.accumulatedWeight >= r) {
                return entry.value
            }
        }
        throw IllegalStateException("WeightedCollection was empty!")
    }

    fun weightedIterator() : Iterator<Pair<E, Double>> {
        return object : Iterator<Pair<E, Double>> {
            private var i = 0

            override fun hasNext(): Boolean {
                return i < orderedEntries.size
            }

            override fun next(): Pair<E, Double> {
                val entry = orderedEntries[i++]
                return Pair(entry.value, entry.originWeight)
            }
        }
    }

    private class Entry<E>(var index: Int, var accumulatedWeight: Double, var originWeight: Double, val value: E)
}