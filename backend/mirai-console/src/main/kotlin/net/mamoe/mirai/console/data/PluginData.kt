/*
 * Copyright 2019-2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AFFERO GENERAL PUBLIC LICENSE version 3 license that can be found via the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:Suppress(
    "INVISIBLE_REFERENCE",
    "INVISIBLE_MEMBER",
    "EXPOSED_SUPER_CLASS",
    "NOTHING_TO_INLINE", "unused"
)
@file:JvmName("PluginDataKt")

package net.mamoe.mirai.console.data

import kotlinx.serialization.KSerializer
import net.mamoe.mirai.console.internal.data.*
import net.mamoe.mirai.console.plugin.jvm.JvmPlugin
import net.mamoe.mirai.console.plugin.jvm.loadPluginData
import net.mamoe.mirai.console.util.ConsoleExperimentalAPI
import net.mamoe.mirai.console.util.ConsoleInternalAPI
import kotlin.internal.LowPriorityInOverloadResolution
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType

/**
 * 一个插件内部的, 对用户隐藏的数据对象. 可包含对多个 [Value] 的值变更的跟踪.
 *
 * [PluginData] 不涉及有关数据的存储, 而是只维护数据结构: [属性节点列表][valueNodes].
 *
 * 有关存储方案, 请查看 [PluginDataStorage].
 *
 * **注意**: [PluginData] 总应该是单例的.
 *
 * ### [JvmPlugin] 的实现方案
 *
 * 要修改保存时的名称, 请参考 [ValueName]
 *
 * ### 使用 Kotlin
 *
 * 在 [JvmPlugin] 的典型实现方式:
 * ```
 * object PluginMain : KotlinPlugin()
 *
 * object MyPluginData : PluginData by PluginMain.loadPluginData() {
 *    val list: MutableList<String> by value(mutableListOf("a", "b")) // mutableListOf("a", "b") 是初始值, 可以省略
 *    val custom: Map<Long, CustomData> by value() // 使用 kotlinx-serialization 序列化的类型. (目前还不支持)
 *    var long: Long by value(0) // 允许 var
 *    var int by value(0) // 可以使用类型推断, 但更推荐使用 `var long: Long by value(0)` 这种定义方式.
 * }
 *
 * @Serializable
 * data class CustomData(
 *     // ...
 * )
 * ```
 *
 * 使用时, 可以方便地直接调用, 如:
 * ```
 * val theList = AccountPluginData.list
 * ```
 *
 * 但也注意, 不要存储 `AccountPluginData.list`. 它可能受不到值跟踪. 若必要存储, 请使用 [PluginData.findBackingFieldValue]
 *
 * ### 使用 Java
 *
 * 参考 [JPluginData]
 *
 * **注意**: 由于实现特殊, 请不要在初始化 Value 时就使用 `.get()`. 这可能会导致自动保存追踪失效. 必须在使用时才调用 `.get()` 获取真实数据对象.
 *
 * @see JvmPlugin.loadPluginData 通过 [JvmPlugin] 获取指定 [PluginData] 实例.
 * @see PluginDataStorage [PluginData] 存储仓库
 */
public interface PluginData {
    /**
     * 添加了追踪的 [ValueNode] 列表 (即使用 `by value()` 委托的属性), 即通过 `by value` 初始化的属性列表.
     *
     * 他们的修改会被跟踪, 并触发 [onValueChanged].
     *
     * @see provideDelegate
     * @see track
     */
    @ConsoleExperimentalAPI
    public val valueNodes: MutableList<ValueNode<*>>

    /**
     * 由 [provideDelegate] 创建, 来自一个通过 `by value` 初始化的属性节点.
     */
    public data class ValueNode<T>(
        /**
         * 节点名称.
         *
         * 如果属性带有 [ValueName], 则使用 [ValueName.value],
         * 否则使用 [属性名称][KProperty.name]
         */
        val valueName: String,
        /**
         * 属性值代理
         */
        val value: Value<out T>,
        /**
         * 属性值更新器
         *
         * @suppress 注意, 这是实验性 API.
         */
        @ConsoleExperimentalAPI
        val updaterSerializer: KSerializer<Unit>
    )

    /**
     * 使用 `by value()` 时自动调用此方法, 添加对 [Value] 的值修改的跟踪, 并创建 [ValueNode] 加入 [valueNodes]
     */
    public operator fun <T : SerializerAwareValue<*>> T.provideDelegate(
        thisRef: Any?,
        property: KProperty<*>
    ): T = track(property.valueName)

    /**
     * 供手动实现时值跟踪使用 (如 Java 用户). 一般 Kotlin 用户需使用 [provideDelegate]
     */
    public fun <T : SerializerAwareValue<*>> T.track(
        /**
         * 值名称.
         *
         * 如果属性带有 [ValueName], 则使用 [ValueName.value],
         * 否则使用 [属性名称][KProperty.name]
         *
         * @see [ValueNode.value]
         */
        valueName: String
    ): T

    /**
     * 所有 [valueNodes] 更新和保存序列化器. 仅供内部使用
     *
     * @suppress 注意, 这是实验性 API.
     */
    @ConsoleExperimentalAPI
    public val updaterSerializer: KSerializer<Unit>

    /**
     * 当所属于这个 [PluginData] 的 [Value] 的 [值][Value.value] 被修改时被调用.
     */
    @ConsoleInternalAPI
    public fun onValueChanged(value: Value<*>)

    /**
     * 当这个 [PluginData] 被放入一个 [PluginDataStorage] 时调用
     */
    @ConsoleInternalAPI
    public fun setStorage(storage: PluginDataStorage)
}

/**
 * 获取这个 [KProperty] 委托的 [Value]
 *
 * 如, 对于
 * ```
 * object MyData : PluginData {
 *     val list: List<String> by value()
 * }
 *
 * val value: Value<List<String>> = MyData.findBackingFieldValue(MyData::list)
 * ```
 */
@Suppress("UNCHECKED_CAST")
public fun <T> PluginData.findBackingFieldValue(property: KProperty<T>): Value<out T>? =
    findBackingFieldValue(property.valueName)

/**
 * 获取这个 [KProperty] 委托的 [Value]
 *
 * 如, 对于
 * ```
 * object MyData : PluginData {
 *     @ValueName("theList")
 *     val list: List<String> by value()
 *     val int: Int by value()
 * }
 *
 * val value: Value<List<String>> = MyData.findBackingFieldValue("theList") // 需使用 @ValueName 标注的名称
 * val intValue: Value<Int> = MyData.findBackingFieldValue("int")
 * ```
 */
@Suppress("UNCHECKED_CAST")
public fun <T> PluginData.findBackingFieldValue(propertyValueName: String): Value<out T>? {
    return this.valueNodes.find { it.valueName == propertyValueName }?.value as Value<T>
}


/**
 * 获取这个 [KProperty] 委托的 [Value]
 *
 * 如, 对于
 * ```
 * object MyData : PluginData {
 *     val list: List<String> by value()
 * }
 *
 * val value: PluginData.ValueNode<List<String>> = MyData.findBackingFieldValueNode(MyData::list)
 * ```
 */
@Suppress("UNCHECKED_CAST")
public fun <T> PluginData.findBackingFieldValueNode(property: KProperty<T>): PluginData.ValueNode<out T>? {
    return this.valueNodes.find { it == property } as PluginData.ValueNode<out T>?
}

/**
 * 用于支持属性委托
 */
@JvmSynthetic
public inline operator fun <T> Value<T>.getValue(thisRef: Any?, property: KProperty<*>): T = value

/**
 * 用于支持属性委托
 */
@JvmSynthetic
public inline operator fun <T> Value<T>.setValue(thisRef: Any?, property: KProperty<*>, value: T) {
    this.value = value
}

//// region PluginData_value_primitives CODEGEN ////

/**
 * 创建一个 [Byte] 类型的 [Value], 并设置初始值为 [default]
 */
public fun PluginData.value(default: Byte): SerializerAwareValue<Byte> = valueImpl(default)

/**
 * 创建一个 [Short] 类型的 [Value], 并设置初始值为 [default]
 */
public fun PluginData.value(default: Short): SerializerAwareValue<Short> = valueImpl(default)

/**
 * 创建一个 [Int] 类型的 [Value], 并设置初始值为 [default]
 */
public fun PluginData.value(default: Int): SerializerAwareValue<Int> = valueImpl(default)

/**
 * 创建一个 [Long] 类型的 [Value], 并设置初始值为 [default]
 */
public fun PluginData.value(default: Long): SerializerAwareValue<Long> = valueImpl(default)

/**
 * 创建一个 [Float] 类型的 [Value], 并设置初始值为 [default]
 */
public fun PluginData.value(default: Float): SerializerAwareValue<Float> = valueImpl(default)

/**
 * 创建一个 [Double] 类型的 [Value], 并设置初始值为 [default]
 */
public fun PluginData.value(default: Double): SerializerAwareValue<Double> = valueImpl(default)

/**
 * 创建一个 [Char] 类型的 [Value], 并设置初始值为 [default]
 */
public fun PluginData.value(default: Char): SerializerAwareValue<Char> = valueImpl(default)

/**
 * 创建一个 [Boolean] 类型的 [Value], 并设置初始值为 [default]
 */
public fun PluginData.value(default: Boolean): SerializerAwareValue<Boolean> = valueImpl(default)

/**
 * 创建一个 [String] 类型的 [Value], 并设置初始值为 [default]
 */
public fun PluginData.value(default: String): SerializerAwareValue<String> = valueImpl(default)

//// endregion PluginData_value_primitives CODEGEN ////


/**
 * 通过具体化类型创建一个 [SerializerAwareValue], 并设置初始值.
 *
 * @param T 具体化参数类型 T. 仅支持:
 * - 基础数据类型
 * - 标准库集合类型 ([List], [Map], [Set])
 * - 标准库数据类型 ([Map.Entry], [Pair], [Triple])
 * - 和使用 [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) 的 [Serializable] 标记的
 */
@Suppress("UNCHECKED_CAST")
@LowPriorityInOverloadResolution
public inline fun <reified T> PluginData.value(default: T): SerializerAwareValue<T> =
    valueFromKType(typeOf0<T>(), default)

/**
 * 通过具体化类型创建一个 [SerializerAwareValue].
 * @see valueFromKType 查看更多实现信息
 */
@LowPriorityInOverloadResolution
public inline fun <reified T> PluginData.value(): SerializerAwareValue<T> =
    valueImpl(typeOf0<T>(), T::class)

@Suppress("UNCHECKED_CAST")
@PublishedApi
internal fun <T> PluginData.valueImpl(type: KType, classifier: KClass<*>): SerializerAwareValue<T> =
    valueFromKType(type, classifier.run { objectInstance ?: createInstanceSmart() } as T)

/**
 * 通过一个特定的 [KType] 创建 [Value], 并设置初始值.
 *
 * 对于 [Map], [Set], [List] 等标准库类型, 这个函数会尝试构造 [LinkedHashMap], [LinkedHashSet], [ArrayList] 等相关类型.
 * 而对于自定义数据类型, 本函数只会反射获取 [objectInstance][KClass.objectInstance] 或使用*无参构造器*构造实例.
 *
 * @param T 具体化参数类型 T. 仅支持:
 * - 基础数据类型, [String]
 * - 标准库集合类型 ([List], [Map], [Set])
 * - 标准库数据类型 ([Map.Entry], [Pair], [Triple])
 * - 使用 [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) 的 [Serializable] 标记的类
 */
@Suppress("UNCHECKED_CAST")
@ConsoleExperimentalAPI
public fun <T> PluginData.valueFromKType(type: KType, default: T): SerializerAwareValue<T> =
    (valueFromKTypeImpl(type) as SerializerAwareValue<Any?>).apply { this.value = default } as SerializerAwareValue<T>

// TODO: 2020/6/24 Introduce class TypeToken for compound types for Java.