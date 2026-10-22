package com.oloomyar.app.billing

import android.content.Context
import android.util.AtomicFile
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal class BazaarReceiptStorage(context: Context) : BillingStorage {
    private val preferences = context.getSharedPreferences("bazaar_receipts_v2", Context.MODE_PRIVATE)
    private val legacy = context.getSharedPreferences("bazaar_entitlements_v1", Context.MODE_PRIVATE)
    private val backup = AtomicFile(File(context.noBackupFilesDir, "bazaar_verified_receipts.json"))
    private var sequence = 0L
    override fun load(): StoredBillingData {
        val primary = runCatching { preferences.getString("snapshot", null) }.getOrNull()
        val secondary = runCatching { backup.readFully().toString(Charsets.UTF_8) }.getOrNull()
        val snapshots = listOfNotNull(primary, secondary).mapNotNull { encoded ->
            runCatching { JSONObject(encoded).optLong("sequence", 0) to BillingSnapshotCodec.decode(encoded) }.getOrNull()
        }
        val newest = snapshots.maxByOrNull { it.first }
        sequence = newest?.first ?: 0
        return if (newest != null) StoredBillingData(
            receipts = snapshots.flatMap { it.second.receipts }.distinct(),
            pending = newest.second.pending,
        ) else StoredBillingData(requiresRestore =
            primary != null || secondary != null || legacy.contains("verified_non_consumable_skus"))
    }
    override fun save(data: StoredBillingData): Boolean {
        // Another chapter/activity may have completed a purchase since this
        // instance loaded. A stale empty writer must never erase that proof.
        val existing = runCatching { load().receipts }.getOrDefault(emptyList())
        val merged = data.copy(receipts = (existing + data.receipts).distinct())
        val encoded = JSONObject(BillingSnapshotCodec.encode(merged)).put("sequence", ++sequence).toString()
        // Two independent atomic copies prevent a broken preference file from
        // making a legitimate customer appear to have lost their purchase.
        val primarySaved = runCatching { preferences.edit().putString("snapshot", encoded).commit() }.getOrDefault(false)
        val backupSaved = runCatching {
            val stream = backup.startWrite()
            try {
                stream.write(encoded.toByteArray(Charsets.UTF_8))
                backup.finishWrite(stream)
            } catch (error: Exception) {
                backup.failWrite(stream)
                throw error
            }
            true
        }.getOrDefault(false)
        val saved = primarySaved || backupSaved
        if (saved) legacy.edit().remove("verified_non_consumable_skus").apply()
        return saved
    }
}

internal object BillingSnapshotCodec {
    fun encode(data: StoredBillingData): String = JSONObject().apply {
        put("receipts", JSONArray().apply {
            data.receipts.forEach { put(JSONObject().put("json", it.json).put("signature", it.signature)) }
        })
        data.pending?.let {
            put("pending", JSONObject().put("sku", it.sku).put("payload", it.payload).put("createdAt", it.createdAt))
        }
    }.toString()
    fun decode(encoded: String): StoredBillingData {
        val objectData = JSONObject(encoded)
        val rows = objectData.getJSONArray("receipts")
        val receipts = (0 until rows.length()).map {
            val row = rows.getJSONObject(it)
            SignedBazaarReceipt(row.getString("json"), row.getString("signature"))
        }
        val pending = objectData.optJSONObject("pending")?.let {
            PendingBazaarPurchase(it.getString("sku"), it.getString("payload"), it.getLong("createdAt"))
        }
        return StoredBillingData(receipts, pending)
    }
}

