package com.ravingarinc.expeditions.queue

import java.util.concurrent.atomic.AtomicReference

class Rotation(val key: String, val set: Set<String>) {
    private val nextMap = AtomicReference(set.random())


    fun getNextMap() : String {
        return nextMap.acquire
    }

    fun randomiseMap() {
        nextMap.setRelease(set.random())
    }


    override fun equals(other: Any?): Boolean {
        if(other == null) return false
        if(other is Rotation) {
            return other.key == this.key
        }
        return false
    }

    override fun hashCode(): Int {
        return key.hashCode()
    }
}