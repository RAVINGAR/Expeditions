package com.ravingarinc.expeditions.api

import kotlin.random.Random

class WeightedCollection<E> : Collection<E> {
    private val entries: MutableMap<E, Entry<E>> = HashMap()
    private val orderedEntries: MutableList<Entry<E>> = ArrayList()
    private var accumulatedWeight: Double = 0.0
    override val size: Int
        get() = entries.size

    override fun isEmpty(): Boolean {
        return entries.isEmpty()
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
        return entries.keys.containsAll(elements)
    }

    override fun contains(element: E): Boolean {
        return entries.contains(element)
    }

    fun add(element: E, weight: Double) {
        accumulatedWeight += weight
        val entry: Entry<E> = Entry(size, accumulatedWeight, element)
        entries[element] = entry
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