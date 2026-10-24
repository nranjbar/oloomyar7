# تطبیق پرداخت هفتم با راهنمای Kotlin بازار

راهنمای [پولکی در سایت رسمی بازار](https://developers.cafebazaar.ir/fa/guidelines/in-app-billing/implementation/kotlin) در ۲۴ اکتبر ۲۰۲۶ کامل خوانده شد. صفحه محتوای خود را با JavaScript از سرویس همان سایت می‌گیرد؛ متن از [منبع محتوای خود صفحه](https://developers.cafebazaar.ir/wp-json/v2/document/single?slug=in-app-billing%2Fimplementation%2Fkotlin&lang=fa) دریافت و با کد SDK رسمی نیز تطبیق داده شد.

## مسیر کلید عمومی

کلید ارائه‌شده توسط صاحب برنامه در `config/bazaar-grade7.properties` ثبت شده است. Gradle آن را به `BuildConfig.BAZAAR_RSA_PUBLIC_KEY` می‌رساند. `rememberGrade7Billing` این مقدار را به `BazaarBillingManager` می‌دهد؛ سپس همان کلید در `PoolakeyGateway` برای `SecurityCheck.Enable` و در `SignedReceiptVerifier` برای بررسی رسیدهای ذخیره‌شده استفاده می‌شود. نیازی به کپی کلید در چند فایل Kotlin نیست.

اثر انگشت SHA-256 بایت‌های DER کلید:

```text
93a592bccda2e6302461e8a5ddf052019e0ad6ced7ae71519567514ecbcada2c
```

کلید از نوع RSA با قالب X.509 SubjectPublicKeyInfo است. ساخت release کلید نامعتبر را نمی‌پذیرد. خروجی CI باید حضور همین کلید در APK و AAB کامپایل‌شده و `REVIEW_MODE=false` در release را نیز تأیید کند.

## تطبیق مراحل راهنما

| مورد | پیاده‌سازی پروژه |
| --- | --- |
| وابستگی و مخزن | پولکی رسمی `2.3.0` و JitPack محدود به گروه همین کتابخانه |
| تنظیم امنیت | `SecurityCheck.Enable(rsaPublicKey = publicKey)` داخل `PaymentConfiguration` |
| اتصال و خطاها | `connect` با رویدادهای موفقیت، خطا و قطع اتصال |
| بستن اتصال | `DisposableEffect`، سپس `manager.close()` و `Connection.disconnect()` |
| خرید محصول عادی | `purchaseProduct` با `activityResultRegistry` و `PurchaseRequest` |
| شناسه و payload | فقط `oloomyar_g7_complete`؛ payload تصادفی و بررسی تطبیق نتیجه |
| اشتراک | محصول دائمی عادی است؛ `shouldSupportSubscription=false` |
| مصرف | هیچ فراخوانی `consumeProduct` وجود ندارد؛ خرید دائمی مصرف نمی‌شود |

## بازیابی و بررسی خرید

راهنما `getPurchasedProducts` را برای دریافت خریدها معرفی می‌کند. این پروژه از خوانندهٔ مستقیم سرویس پرداخت بازار، `BazaarInventoryReader`، استفاده می‌کند تا تمام صفحه‌های `getPurchases` را پیش از تحویل یک نتیجه جمع کند. سپس `SignedReceiptVerifier` امضای تک‌تک رسیدها و سیاست خرید، نام بسته و محصول را بررسی می‌کنند. این خواننده جایگزین سفارشی بخش دریافت فهرست است و فراخوانی مستقیم متد پولکی نیست. دلیل آن، حفظ رفتار آزموده‌شدهٔ بازیابی کامل و جلوگیری از تصمیم‌گیری روی پاسخ ناقص است؛ [کد رسمی QueryFunction](https://github.com/cafebazaar/Poolakey/blob/2.3.0/poolakey/src/main/java/ir/cafebazaar/poolakey/billing/query/QueryFunction.kt) نیز بررسی شد.

راهنما تأیید خرید از طریق API سمت سرور را توصیه می‌کند. پروژهٔ حاضر سرور پرداخت جداگانه ندارد؛ تأیید امضا روی دستگاه انجام می‌شود. رسید امضاشده برای حفظ دسترسی آفلاین در فضای خصوصی برنامه، با پشتیبان مستقل، نگهداری و پیش از استفاده دوباره اعتبارسنجی می‌شود. این پیاده‌سازی، تأیید سمت سرور را انجام نمی‌دهد. تراکنش واقعی، فعال‌بودن محصول و تطبیق کلید با برنامهٔ پیشخوان باید روی نسخهٔ انتشار و حساب بازار صاحب برنامه آزمایش شوند.

## منابع تکمیلی

- [انتشار رسمی پولکی ۲٫۳٫۰](https://github.com/cafebazaar/Poolakey/releases/tag/2.3.0)
- [نمونهٔ رسمی اتصال و خرید](https://github.com/cafebazaar/Poolakey/blob/2.3.0/app/src/main/java/ir/cafebazaar/poolakeysample/MainActivity.kt)
- [تعریف رسمی SecurityCheck](https://github.com/cafebazaar/Poolakey/blob/2.3.0/poolakey/src/main/java/ir/cafebazaar/poolakey/config/SecurityCheck.kt)
- [فرم محصول، ساخت AAB و BIN و آزمایش انتشار](BAZAAR_RELEASE_AAB_BIN_FA.md)
