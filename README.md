# Ninja Open Source (`delivery.samurai.android` — بدون LSPosed)

مشروع مفتوح المصدر يعمل بنفس أسلوب **SAMURAI.apk** (بدون LSPosed وبدون روت) باستخدام مكتبة **Pine** (`top.canyie.pine:core`) للربط الداخلي وقت التشغيل (In-App Runtime Hooking)، مع **GitHub Actions Auto-Patcher** لدمج المميزات في أي إصدار من `orgnail.apk`.

## المميزات الست
- **Auto Clicker**: النقر التلقائي على أزرار قبول/تأكيد الطلبات بسرعة قابلة للتخصيص (ms).
- **Show Support**: إظهار خيارات الدعم الفني + اختصار لفتح شاشة الدعم مباشرة من لوحة التحكم.
- **Customer Location**: تفعيل `OrderTask.shouldShowLocationIcon` لإظهار أيقونة موقع العميل.
- **Fake GPS**: تغيير الإحداثيات وتجاوز `isFromMockProvider` / `AllowMockProvider` / `FakeGpsEnforcementCoordinator`.
- **Auto Refresh**: تحديث `OrdersFragment` / `SwipeRefreshLayout` تلقائياً بفاصل زمني قابل للتخصيص.
- **Display Order Number**: عرض رقم الطلب (`orderNumber`) مباشرة أعلى شاشة الطلب.

## طريقة الاستخدام مع أي إصدار من `orgnail.apk`
1. ضع أي نسخة من `orgnail.apk` داخل المسار `original/orgnail.apk` في المستودع (أو مرّر رابط التحميل المباشر في `workflow_dispatch`).
2. شغّل الـ Workflow: `.github/workflows/patch-apk.yml`.
3. سيقوم الـ Workflow تلقائياً بـ:
   - بناء كود الإضافات مفتوح المصدر (`NinjaMod` + `Pine`).
   - فك `orgnail.apk` عبر `apktool` وحقن سطر `NinjaMod.init(this)` داخل `AndroidApp.onCreate()`.
   - نسخ حزم المود ومكتبة `libpine.so` وإعادة البناء والتوقيع بـ `apksigner`.
4. حمّل ملف `samurai-open-source-patched.apk` من تبويب **Actions** وثبّته مباشرة على الهاتف، ثم اضغط الزر العائم **N** لفتح لوحة التحكم.
