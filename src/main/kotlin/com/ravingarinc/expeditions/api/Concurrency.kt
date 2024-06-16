package com.ravingarinc.expeditions.api

import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import java.util.concurrent.atomic.AtomicReference
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

fun <T> atomic(valIn: T) : ReadWriteProperty<Any?, T> {
    return object : ReadWriteProperty<Any?, T> {
        val t = AtomicReference(valIn)

        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
            return t.get()
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            t.set(value)
        }
    }
}

fun <T> atomicNullable(valIn: T?) : ReadWriteProperty<Any?, T?> {
    return object : ReadWriteProperty<Any?, T?> {
        val t = AtomicReference(valIn)

        override fun getValue(thisRef: Any?, property: KProperty<*>): T? {
            return t.get()
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
            t.set(value)
        }
    }
}

