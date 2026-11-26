# QobearVPN

کلاینت اختصاصی اندروید **OpenVPN 3** برای سرورهای TunnelBear با زبان طراحی **Nothing OS**، انیمیشن‌های روان، پینگ واقعی هندشیک TLS، بایپس هم‌زمان GeoIP و DPI برای **ایران، چین، روسیه و جهانی**، اسپلیت تانلینگ و کاشی تنظیمات سریع (Quick Settings Tile).

## ویژگی‌های کلیدی
- **۴۷ پروفایل داخلی TunnelBear:** اتصال مستقیم با هسته بومی OpenVPN 3 (`MPL-2.0`) + `Mbed TLS 3.6.7`.
- **حالت‌های بایپس دوگانه (ایران / چین / روسیه / جهانی):**
  - **ایران / چین / روسیه:** عبور مستقیم IPهای داخلی و شبکه محلی (LAN) همراه با لایه ضدسانسور **DPI Split Relay** (تکه‌تکه‌سازی بایت‌های ابتدایی TLS پس از Reset و تنظیم `TCP_WINDOW_CLAMP`).
  - **جهانی:** تونل کامل بدون بایپس کشوری و بدون سربار Relay (با قابلیت Fallback خودکار در صورت نیاز).
- **پینگ واقعی OpenVPN + اتصال خودکار:** آزمایش هم‌زمان IPهای پشت `*.lazerpenguin.com` با هندشیک واقعی TLS (تکی، دسته‌جمعی همه سرورها، یا انتخاب خودکار سریع‌ترین سرور) همراه با پرچم کشورها.
- **قابلیت‌های کامل VPN:** اسپلیت تانلینگ برنامه‌ها (همه / فقط منتخب / بایپس منتخب + پیش‌تنظیم اپ‌های ایرانی)، Quick Settings Tile، نوتیفیکیشن پایدار با سرعت زنده و دکمه‌های قطع/تغییر سریع، انتخابگر DNS، Always-On و ذخیره‌سازی رمزنگاری‌شده با `AndroidKeyStore`.
- **طراحی Nothing OS:** تم OLED Dark و Paper Light، فونت‌های Vazirmatn و DotGothic16 / SpaceMono، دایره ماتریس نقطه‌ای انیمیشنی و لینک مستقیم کانال `@Qorvhex_Channel`.

## خروجی گرفتن آسان با GitHub Actions
۱. پروژه را در یک ریپازیتوری GitHub آپلود (Push) کنید.
۲. در تب **Actions** ورک‌فلوی **Build QobearVPN APKs** را اجرا کنید (یا با پوش روی `main` / تگ `v*` خودکار اجرا می‌شود).
۳. از بخش **Artifacts** فایل‌های `universal`، `arm64-v8a` و `armeabi-v7a` را در دو نسخه Debug و Release دانلود و نصب کنید.

### امضای اختصاصی Release (اختیاری)
در صورت تمایل به امضا با Keystore شخصی، ۴ مقدار زیر را در `Settings -> Secrets and variables -> Actions` اضافه کنید:
- `QOBEAR_KEYSTORE_BASE64`
- `QOBEAR_KEYSTORE_PASSWORD`
- `QOBEAR_KEY_ALIAS`
- `QOBEAR_KEY_PASSWORD`

در صورت نبود این Secrets، ورک‌فلو به‌صورت خودکار با کلید پیش‌فرض امضا می‌کند تا APKهای خروجی بدون خطا نصب شوند.

## بیلد محلی
```bash
python3 scripts/fetch_openvpn_deps.py
./gradlew testDebugUnitTest assembleDebug
```
