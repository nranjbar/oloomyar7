package com.oloomyar.app.billing

import org.json.JSONObject
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

internal object BillingReceiptFixtures {
    const val PACKAGE = "com.oloomyar.app.grade7"
    val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val publicKey = Base64.getEncoder().encodeToString(key.public.encoded)
    fun verifier() = SignedReceiptVerifier(publicKey) { Base64.getDecoder().decode(it) }
    fun receipt(
        sku: String = BazaarProducts.COMPLETE, payload: String = "nonce",
        appPackage: String = PACKAGE, state: Int = 0, time: Long = 1_000,
        token: String = "token-$sku", transform: (JSONObject) -> Unit = {},
    ): SignedBazaarReceipt {
        val json = JSONObject().put("productId", sku).put("developerPayload", payload)
            .put("packageName", appPackage).put("purchaseState", state)
            .put("purchaseTime", time).put("purchaseToken", token).put("orderId", "order-$sku")
        transform(json)
        return sign(json.toString())
    }
    fun sign(json: String): SignedBazaarReceipt {
        val signer = Signature.getInstance("SHA1withRSA")
        signer.initSign(key.private)
        signer.update(json.toByteArray(Charsets.UTF_8))
        return SignedBazaarReceipt(json, Base64.getEncoder().encodeToString(signer.sign()))
    }
}

