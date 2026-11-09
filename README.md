# جزیره هایپر (HyperIsland)

جزیره‌ی پویا (Dynamic Island) برای اندروید، شبیه Hyper Island شیائومی؛ موزیک، تماس، پیام، تایمر/دانلود/مسیریابی، شارژ و رویدادهای سیستمی. تنظیمات کاملاً شخصی‌سازی‌شونده و زنده.

## ساخت APK
- **GitHub Actions:** پروژه را در یک ریپو بگذار و push کن؛ از تب Actions فایل `HyperIsland-debug-apk` را بردار.
- **Android Studio:** پوشه را باز کن و Run / Build APK بزن (JDK 17، compileSdk 34).

## راه‌اندازی روی گوشی
1. برنامه را باز کن و «سرویس دسترسی‌پذیری» و «دسترسی اعلان‌ها» را روشن کن
   (اندروید ۱۳+: اطلاعات برنامه ← ⋮ ← اجازه‌ی تنظیمات محدودشده).
2. مجوز «تماس و مخاطبین» را بده.
3. شیائومی: Autostart و «بدون محدودیت باتری» را بده و برنامه را در Recents **قفل** کن.
4. با اسلایدر «فاصله از بالا» جزیره را روی دوربین جلو تنظیم کن.

## ساختار
- `IslandView` ظاهر/ژست‌ها، `IslandController` انیمیشن فنری و صف رویدادها
- `MediaMonitor` / `CallMonitor` / `SystemMonitor` / `HyperNotificationListener` منابع رویداد
- `IslandAccessibilityService` میزبان اصلی (ماندگار)، `IslandService` نگهبان بقا
