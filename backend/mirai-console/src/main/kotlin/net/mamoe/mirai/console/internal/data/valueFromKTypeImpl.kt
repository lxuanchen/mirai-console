/*
 * Copyright 2019-2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AFFERO GENERAL PUBLIC LICENSE version 3 license that can be found via the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:Suppress("NOTHING_TO_INLINE", "INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package net.mamoe.mirai.console.internal.data

import kotlinx.serialization.KSerializer
import net.mamoe.mirai.console.data.PluginData
import net.mamoe.mirai.console.data.SerializableValue.Companion.serializableValueWith
import net.mamoe.mirai.console.data.SerializerAwareValue
import net.mamoe.mirai.console.data.valueFromKType
import net.mamoe.mirai.console.internal.command.qualifiedNameOrTip
import kotlin.reflect.KClass
import kotlin.reflect.KType

private val primitiveCollectionsImplemented by lazy {
    false
}

@PublishedApi
@OptIn(ExperimentalStdlibApi::class)
internal inline fun <reified T> typeOf0(): KType = kotlin.reflect.typeOf<T>()

@Suppress("UnsafeCall", "SMARTCAST_IMPOSSIBLE", "UNCHECKED_CAST")
internal fun PluginData.valueFromKTypeImpl(type: KType): SerializerAwareValue<*> {
    val classifier = type.classifier
    require(classifier is KClass<*>)

    if (classifier.isPrimitiveOrBuiltInSerializableValue()) {
        return valueImplPrimitive(classifier) as SerializerAwareValue<*>
    }

    // TODO: 2020/6/24 优化性能: 预先根据类型生成 V -> Value<V> 的 mapper

    when (classifier) {
        MutableMap::class,
        Map::class,
        LinkedHashMap::class,
        HashMap::class
        -> {
            val keyClass = type.arguments[0].type?.classifier
            require(keyClass is KClass<*>)

            val valueClass = type.arguments[1].type?.classifier
            require(valueClass is KClass<*>)

            if (primitiveCollectionsImplemented && keyClass.isPrimitiveOrBuiltInSerializableValue() && valueClass.isPrimitiveOrBuiltInSerializableValue()) {
                // PrimitiveIntIntMap
                // ...
                TODO()
            } else {
                return createCompositeMapValueImpl<Any?, Any?>(
                    kToValue = { k -> valueFromKType(type.arguments[0].type!!, k) },
                    vToValue = { v -> valueFromKType(type.arguments[1].type!!, v) }
                ).serializableValueWith(serializerMirai(type) as KSerializer<Map<Any?, Any?>>) // erased
            }
        }
        MutableList::class,
        List::class,
        ArrayList::class
        -> {
            val elementClass = type.arguments[0].type?.classifier
            require(elementClass is KClass<*>)

            if (primitiveCollectionsImplemented && elementClass.isPrimitiveOrBuiltInSerializableValue()) {
                // PrimitiveIntList
                // ...
                TODO()
            } else {
                return createCompositeListValueImpl<Any?> { v -> valueFromKType(type.arguments[0].type!!, v) }
                    .serializableValueWith(serializerMirai(type) as KSerializer<List<Any?>>)
            }
        }
        MutableSet::class,
        Set::class,
        LinkedHashSet::class,
        HashSet::class
        -> {
            val elementClass = type.arguments[0].type?.classifier
            require(elementClass is KClass<*>)

            if (primitiveCollectionsImplemented && elementClass.isPrimitiveOrBuiltInSerializableValue()) {
                // PrimitiveIntSet
                // ...
                TODO()
            } else {
                return createCompositeSetValueImpl<Any?> { v -> valueFromKType(type.arguments[0].type!!, v) }
                    .serializableValueWith(serializerMirai(type) as KSerializer<Set<Any?>>)
            }
        }
        else -> error("Custom composite value is not supported yet (${classifier.qualifiedName})")
    }
}

internal fun KClass<*>.createInstanceSmart(): Any? {
    return when (this) {
        Byte::class -> 0.toByte()
        Short::class -> 0.toShort()
        Int::class -> 0
        Long::class -> 0L
        Float::class -> 0.toFloat()
        Double::class -> 0.0

        Boolean::class -> false

        String::class -> ""

        MutableMap::class,
        Map::class,
        LinkedHashMap::class,
        HashMap::class
        -> LinkedHashMap<Any?, Any?>()

        MutableList::class,
        List::class,
        ArrayList::class
        -> ArrayList<Any?>()

        MutableSet::class,
        Set::class,
        LinkedHashSet::class,
        HashSet::class
        -> LinkedHashSet<Any?>()

        else -> createInstanceOrNull()
            ?: error("Cannot create instance or find a initial value for ${this.qualifiedNameOrTip}")
    }
}

internal fun KClass<*>.isPrimitiveOrBuiltInSerializableValue(): Boolean {
    when (this) {
        Byte::class, Short::class, Int::class, Long::class,
        Boolean::class,
        Char::class, String::class,
        Pair::class, Triple::class // TODO: 2020/6/24 支持 PairValue, TripleValue
        -> return true
    }

    return false
}

@PublishedApi
@Suppress("UNCHECKED_CAST")
internal inline fun <R> Any.cast(): R = this as R
