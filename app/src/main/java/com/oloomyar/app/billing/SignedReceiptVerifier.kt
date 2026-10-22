package com.oloomyar.app.billing

import ir.cafebazaar.poolakey.entity.PurchaseInfo
import ir.cafebazaar.poolakey.entity.PurchaseState
import org.json.JSONObject
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

internal class SignedReceiptVerifier(
    publicKey: String,
    private val decodeBase64: (String) -> ByteArray,
) : BazaarReceiptVerifier {
    private val key = runCatching {
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(decodeBase64(publicKey.trim())))
    }.getOrNull()
    override val configured: Boolean get() = key != null

    override fun verify(receipt: SignedBazaarReceipt): PurchaseInfo {
        try {
            val verifier = Signature.getInstance("SHA1withRSA")
            verifier.initVerify(key ?: throw InvalidBazaarReceipt())
            verifier.update(receipt.json.toByteArray(Charsets.UTF_8))
            if (!verifier.verify(decodeBase64(receipt.signature))) throw InvalidBazaarReceipt()
            val data = JSONObject(receipt.json)
            val state = when (data.getInt("purchaseState")) {
                0 -> PurchaseState.PURCHASED
                1, 2 -> PurchaseState.REFUNDED
                else -> throw InvalidBazaarReceipt()
            }
            val token = data.getString("purchaseToken").takeIf { it.isNotBlank() } ?: throw InvalidBazaarReceipt()
            val product = data.getString("productId").takeIf { it.isNotBlank() } ?: throw InvalidBazaarReceipt()
            val appPackage = data.getString("packageName").takeIf { it.isNotBlank() } ?: throw InvalidBazaarReceipt()
            val time = data.getLong("purchaseTime").takeIf { it > 0 } ?: throw InvalidBazaarReceipt()
            return PurchaseInfo(
                orderId = data.optString("orderId"), purchaseToken = token,
                payload = data.optString("developerPayload"), packageName = appPackage,
                purchaseState = state, purchaseTime = time, productId = product,
                originalJson = receipt.json, dataSignature = receipt.signature,
            )
        } catch (error: Exception) {
            throw InvalidBazaarReceipt()
        }
    }
}

