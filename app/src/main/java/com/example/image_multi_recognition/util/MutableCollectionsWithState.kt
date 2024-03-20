package com.example.image_multi_recognition.util

import androidx.compose.runtime.mutableStateOf

// Normally, the immutableSet is used as the state like:
//      var set by rememberSaveable{ mutableStateOf(emptySet<ElementType>()) }
// And you use set += element, set -= element to change the set,
// However, += and -= copies the original set to create a new set, which can be an overhead if the set contains lot of elements
// To avoid such overhead, use MutableSetWithState below and track the State: version properly
// e.g.:
//    val selected by remember{
//        derivedStateOf {
//            // read the State version.value to get "selected" automatically when state is change
//            selectedImageIdSet.version.value
//            selectedImageIdSet.contains(pageItem.imageInfo.id)
//        }
//    }
data class MutableSetWithState<E>(
    val mutableSet: MutableSet<E> = mutableSetOf()
) : MutableSet<E> by mutableSet {
    val version = mutableStateOf(0)

    // version.value * 0: causes a memory read to "version" State and when "version" state is changed,
    // the recomposition happens in the composable that calls MutableSetWithState.size
    // so that the composible can always get the updated size when mutableSet is modified
    override val size: Int
        get() = version.value * 0 + mutableSet.size


    override fun add(element: E): Boolean =
        mutableSet.add(element).apply {
            version.value += 1
            //Log.d(getCallSiteInfo(), "label changed to ${version.value}")
        }

    override fun remove(element: E): Boolean =
        mutableSet.remove(element).apply {
            version.value += 1
            //Log.d(getCallSiteInfo(), "label changed to ${version.value}")
        }
    // Bulk Modification Operations

    override fun addAll(elements: Collection<E>): Boolean =
        mutableSet.addAll(elements).apply {
            version.value += 1
            //Log.d(getCallSiteInfo(), "label changed to ${version.value}")
        }

    override fun removeAll(elements: Collection<E>): Boolean =
        mutableSet.removeAll(elements).apply {
            version.value += 1
            //Log.d(getCallSiteInfo(), "label changed to ${version.value}")
        }

    override fun retainAll(elements: Collection<E>): Boolean =
        mutableSet.retainAll(elements).apply {
            version.value += 1
            //Log.d(getCallSiteInfo(), "label changed to ${version.value}")
        }

    override fun clear(): Unit =
        mutableSet.clear().apply {
            version.value += 1
            //Log.d(getCallSiteInfo(), "label changed to ${version.value}")
        }
}

data class MutableMapWithState<K, V>(
    val mutableMap: MutableMap<K, V> = mutableMapOf(),
) : MutableMap<K, V> by mutableMap {
    val version = mutableStateOf(0)

    // override mutable methods
    override fun clear() {
        mutableMap.clear().apply { version.value += 1 }
    }

    override fun remove(key: K): V? =
        mutableMap.remove(key).apply { version.value += 1 }

    override fun putAll(from: Map<out K, V>) {
        mutableMap.putAll(from).apply { version.value += 1 }
    }

    override fun put(key: K, value: V): V? =
        mutableMap.put(key, value).apply { version.value += 1 }
}