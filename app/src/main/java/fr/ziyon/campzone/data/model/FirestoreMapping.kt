package fr.ziyon.campzone.data.model

import com.google.firebase.Timestamp
import java.util.Date

/**
 * Shared Firestore `Map<String, Any?>` decode helpers for the wire models in
 * this package. All Firestore (de)serialization is done by hand (no
 * `toObject<T>()`) so the delete-when-nil / explicit-null / omit-when-nil rules
 * in `07-data-contract-rules.md` can be expressed exactly.
 *
 * Firestore hands numbers back as [Long]/[Double] and timestamps as
 * [Timestamp]; these helpers normalise those into Kotlin types.
 */

/** Trimmed, non-blank String, or null. Use for fields where blank == absent. */
internal fun Map<String, Any?>.stringValue(key: String): String? =
    (this[key] as? String)?.trim()?.takeUnless { it.isBlank() }

/** Raw String exactly as stored (preserves `""`), or null when the key is absent/non-string. */
internal fun Map<String, Any?>.rawStringValue(key: String): String? =
    this[key] as? String

internal fun Map<String, Any?>.intValue(key: String): Int? =
    when (val value = this[key]) {
        is Int -> value
        is Long -> value.toInt()
        is Double -> value.toInt()
        else -> null
    }

internal fun Map<String, Any?>.longValue(key: String): Long? =
    when (val value = this[key]) {
        is Long -> value
        is Int -> value.toLong()
        is Double -> value.toLong()
        else -> null
    }

internal fun Map<String, Any?>.doubleValue(key: String): Double? =
    when (val value = this[key]) {
        is Double -> value
        is Float -> value.toDouble()
        is Int -> value.toDouble()
        is Long -> value.toDouble()
        else -> null
    }

internal fun Map<String, Any?>.boolValue(key: String): Boolean? =
    this[key] as? Boolean

/** Trimmed, non-blank String list (empties dropped). */
internal fun Map<String, Any?>.stringListValue(key: String): List<String> =
    (this[key] as? List<*>)
        ?.mapNotNull { (it as? String)?.trim()?.takeUnless { value -> value.isBlank() } }
        .orEmpty()

/** Raw String list preserving each element verbatim. */
internal fun Map<String, Any?>.rawStringListValue(key: String): List<String> =
    (this[key] as? List<*>)
        ?.mapNotNull { it as? String }
        .orEmpty()

internal fun Map<String, Any?>.dateValue(key: String): Date? =
    when (val value = this[key]) {
        is Timestamp -> value.toDate()
        is Date -> value
        else -> null
    }

@Suppress("UNCHECKED_CAST")
internal fun Map<String, Any?>.mapValue(key: String): Map<String, Any?>? =
    this[key] as? Map<String, Any?>

@Suppress("UNCHECKED_CAST")
internal fun Map<String, Any?>.mapListValue(key: String): List<Map<String, Any?>> =
    (this[key] as? List<*>)
        ?.mapNotNull { it as? Map<String, Any?> }
        .orEmpty()
