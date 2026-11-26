package com.qorvhex.qobearvpn.ui.theme

import com.qorvhex.qobearvpn.model.AppLanguage
import com.qorvhex.qobearvpn.model.BypassRegion

class QobearStrings(val fa: Boolean) {
    val appTitle = "QOBEAR"
    val telegramHandle = "@Qorvhex_Channel"

    // Navigation
    val navConnect = if (fa) "اتصال" else "Connect"
    val navServers = if (fa) "سرورها" else "Servers"
    val navSplit = if (fa) "اسپلیت" else "Split"
    val navSettings = if (fa) "تنظیمات" else "Settings"

    // Onboarding
    val onboardWelcomeTitle = if (fa) "به QobearVPN خوش آمدید" else "Welcome to QobearVPN"
    val onboardWelcomeSub = if (fa) {
        "کلاینت اختصاصی OpenVPN با هسته بومی ضدسانسور برای سرورهای TunnelBear در سبک Nothing OS"
    } else {
        "Purpose-built OpenVPN 3 client for TunnelBear with adaptive DPI bypass and Nothing OS precision."
    }
    val chooseBypassTitle = if (fa) "مسیر و روش بایپس را انتخاب کنید" else "Choose your bypass regime"
    val chooseBypassSub = if (fa) {
        "سایت‌ها و برنامه‌های داخلی کشور انتخاب‌شده بدون فیلترشکن باز می‌شوند و ترافیک بین‌الملل با ضدسانسور مخصوص آن شبکه تونل می‌گردد."
    } else {
        "Domestic IP blocks & LAN traffic route directly while international traffic uses regime-tuned DPI evasion."
    }
    val next = if (fa) "ادامه" else "Continue"
    val back = if (fa) "بازگشت" else "Back"
    val enterCredentialsTitle = if (fa) "اطلاعات اکانت TunnelBear" else "TunnelBear Credentials"
    val enterCredentialsSub = if (fa) {
        "نام کاربری (ایمیل) و رمز عبور تانل‌بر خود را وارد کنید. اطلاعات با کلید سخت‌افزاری AndroidKeyStore رمزنگاری می‌شوند."
    } else {
        "Enter your TunnelBear email and password. Stored encrypted inside AndroidKeyStore AES-GCM."
    }
    val usernameLabel = if (fa) "نام کاربری / ایمیل TunnelBear" else "TunnelBear Email / Username"
    val passwordLabel = if (fa) "رمز عبور" else "Password"
    val createAccountHint = if (fa) "اکانت ندارید؟ ساخت حساب رایگان TunnelBear" else "Need an account? Sign up at TunnelBear"
    val finishAndEnter = if (fa) "ذخیره و ورود به برنامه" else "Save & Start"
    val credentialValidationError = if (fa) "لطفاً نام کاربری و رمز عبور معتبر وارد کنید." else "Enter a valid username and password."

    // Bypass regions
    fun regionTitle(r: BypassRegion): String = when (r) {
        BypassRegion.IRAN -> if (fa) "بایپس ایران" else "Bypass Iran"
        BypassRegion.CHINA -> if (fa) "بایپس چین" else "Bypass China"
        BypassRegion.RUSSIA -> if (fa) "بایپس روسیه" else "Bypass Russia"
        BypassRegion.GLOBAL -> if (fa) "حالت جهانی" else "Global Tunnel"
    }

    fun regionDesc(r: BypassRegion): String = when (r) {
        BypassRegion.IRAN -> if (fa) {
            "عبور مستقیم آی‌پی‌های ایران + بانک‌ها و شبکه محلی | ضدسانسور فعال Split Relay"
        } else {
            "Direct IR domestic routes & LAN | Aggressive TCP-TLS Split Relay"
        }
        BypassRegion.CHINA -> if (fa) {
            "عبور مستقیم آی‌پی‌های چین و شبکه محلی | ضدسانسور بهینه‌شده برای GFW"
        } else {
            "Direct CN routes & LAN | Fine-grained GFW chunking & window clamp"
        }
        BypassRegion.RUSSIA -> if (fa) {
            "عبور مستقیم آی‌پی‌های روسیه و شبکه محلی | ضدسانسور بهینه‌شده برای TSPU"
        } else {
            "Direct RU routes & LAN | TSPU packet shaping & pool failover"
        }
        BypassRegion.GLOBAL -> if (fa) {
            "تونل کامل ۱۰۰٪ بدون بایپس کشوری | اتصال مستقیم حداکثر سرعت"
        } else {
            "100% tunneled traffic | Direct OpenVPN 3 without relay overhead"
        }
    }

    // Home
    val tapToConnect = if (fa) "برای اتصال ضربه بزنید" else "TAP TO CONNECT"
    val tapToCancel = if (fa) "در حال اتصال... (ضربه برای لغو)" else "CONNECTING... (TAP TO CANCEL)"
    val tapToDisconnect = if (fa) "متصل و ایمن" else "PROTECTED"
    val disconnecting = if (fa) "در حال قطع اتصال..." else "DISCONNECTING..."
    val verifyingTunnel = if (fa) "در حال تایید عبور ترافیک..." else "VERIFYING TUNNEL..."
    val activeServer = if (fa) "سرور انتخابی" else "ACTIVE NODE"
    val autoFastest = if (fa) "انتخاب خودکار بهترین" else "Auto Fastest"
    val changeServer = if (fa) "تغییر سرور" else "Switch"
    val bypassModeChip = if (fa) "بایپس" else "Bypass"
    val downloadLabel = if (fa) "دانلود" else "DOWN"
    val uploadLabel = if (fa) "آپلود" else "UP"
    val totalTraffic = if (fa) "حجم کل" else "TOTAL"
    val exitIpLabel = if (fa) "آی‌پی خروجی" else "EXIT IP"

    // Servers
    val serversTitle = if (fa) "سرورهای TunnelBear" else "TunnelBear Nodes"
    val pingAll = if (fa) "پینگ همه" else "Ping All"
    val stopPing = if (fa) "توقف پینگ" else "Stop"
    val autoConnectCardTitle = if (fa) "حالت خودکار (سریع‌ترین سرور)" else "Auto Connect · Best Node"
    val autoConnectCardSub = if (fa) {
        "آزمایش واقعی هندشیک TLS و اتصال خودکار به کم‌تأخیرترین کشور"
    } else {
        "Tests real TLS handshakes and connects to the fastest reachable server"
    }
    val searchServersHint = if (fa) "جستجوی کشور..." else "Search country..."
    val filterAll = if (fa) "همه" else "All"
    val filterFav = if (fa) "برگزیده‌ها" else "Favorites"
    val filterReachable = if (fa) "سالم‌ها" else "Reachable"
    val sortPing = if (fa) "بر اساس پینگ" else "Fastest"
    val pingSingleHint = if (fa) "پینگ تکی" else "Ping"
    val importOvpn = if (fa) "افزودن .ovpn" else "Import .ovpn"
    val notTested = if (fa) "تست نشده" else "Untested"
    val blockedOrTimeout = if (fa) "بدون پاسخ" else "Blocked"
    val controlAuthUnprobeable = if (fa) "پروفایل رمزدار (تست مستقیم)" else "Auth Key Profile"
    val probingNow = if (fa) "در حال پینگ..." else "Probing..."

    // Split Tunneling
    val splitTitle = if (fa) "اسپلیت تانلینگ" else "Split Tunneling"
    val splitAll = if (fa) "همه برنامه‌ها" else "All Apps"
    val splitInclude = if (fa) "فقط انتخابی‌ها" else "Allow Only"
    val splitExclude = if (fa) "بایپس انتخابی‌ها" else "Bypass Selected"
    val splitAllSub = if (fa) "تمام برنامه‌های گوشی از تانل VPN عبور می‌کنند." else "All apps use the VPN tunnel."
    val splitIncludeSub = if (fa) "تنها برنامه‌های تیک‌خورده از VPN استفاده می‌کنند." else "Only checked apps route through VPN."
    val splitExcludeSub = if (fa) "برنامه‌های تیک‌خورده مستقیم (بدون VPN) متصل می‌شوند." else "Checked apps bypass the VPN directly."
    val searchAppsHint = if (fa) "جستجوی برنامه..." else "Search apps..."
    val showSystemApps = if (fa) "نمایش برنامه‌های سیستمی" else "Show system apps"
    val iranBankPreset = if (fa) "افزودن خودکار اپ‌های بانکی و داخلی ایران" else "Auto-add Iranian Banking & Local Apps"

    // Settings
    val settingsTitle = if (fa) "تنظیمات" else "Settings"
    val sectionAccount = if (fa) "حساب کاربری TunnelBear" else "TunnelBear Account"
    val addAccount = if (fa) "افزودن اکانت" else "Add Account"
    val sectionNetwork = if (fa) "شبکه و ضدسانسور" else "Network & Bypass"
    val dnsLabel = if (fa) "سرویس‌دهنده DNS" else "DNS Resolver"
    val lanBypassLabel = if (fa) "بایپس شبکه محلی (LAN)" else "Bypass Local Network (LAN)"
    val autoReconnectLabel = if (fa) "اتصال خودکار هنگام تغییر شبکه" else "Auto-Reconnect on Network Switch"
    val globalFallbackLabel = if (fa) "تغییر خودکار به Split Relay در حالت جهانی" else "Global Auto-Fallback to DPI Relay"
    val autoServerLabel = if (fa) "پینگ خودکار هنگام نبود سرور سریع" else "Auto-Probe when Fastest Unknown"
    val alwaysOnSystem = if (fa) "تنظیمات Always-On و Kill Switch اندروید" else "Android Always-On & Kill Switch"
    val batteryOpt = if (fa) "مستثنی کردن از محدودیت باتری" else "Disable Battery Optimization"
    val sectionAppearance = if (fa) "ظاهر و زبان" else "Appearance & Language"
    val themeLabel = if (fa) "تم برنامه" else "Theme"
    val languageLabel = if (fa) "زبان رابط" else "Language"
    val viewLogs = if (fa) "مشاهده گزارش زنده هسته (Logs)" else "Live Engine Logs"
    val clearLogs = if (fa) "پاک کردن" else "Clear"

    // Storage Recovery
    val storageErrorTitle = if (fa) "خطای بازگشایی حافظه امن" else "Secure Storage Unavailable"
    val storageErrorSub = if (fa) {
        "کلید رمزنگاری حافظه امن باز نشد (ممکن است پس از بازیابی نسخه پشتیبان رخ دهد). برای ادامه، حافظه امن را بازنشانی کنید."
    } else {
        "The encrypted vault could not be opened. Reset secure storage to continue."
    }
    val resetVaultAction = if (fa) "بازنشانی حافظه امن" else "Reset Secure Storage"

    fun bannerText(code: String?): String {
        if (code.isNullOrBlank()) return ""
        return when {
            code.startsWith("IMPORTED:") -> {
                val n = code.removePrefix("IMPORTED:")
                if (fa) "$n پروفایل با موفقیت اضافه شد." else "$n profile(s) imported."
            }
            code.startsWith("IMPORT_DUPLICATE:") -> {
                if (fa) "این پروفایل قبلاً در لیست وجود دارد." else "Profile already exists."
            }
            code.startsWith("IMPORT_ERROR:") -> {
                val err = code.removePrefix("IMPORT_ERROR:")
                if (fa) "خطا در خواندن فایل: $err" else "Import failed: $err"
            }
            code.startsWith("PRESET_ADDED:") -> {
                val n = code.removePrefix("PRESET_ADDED:")
                if (fa) "$n برنامه بانکی/داخلی به لیست بایپس اضافه شد." else "Added $n local apps to bypass list."
            }
            code == "PRESET_NONE" -> {
                if (fa) "اپ بانکی/داخلی شناخته‌شده‌ای روی دستگاه یافت نشد." else "No known local banking apps found."
            }
            code == "AUTO_PROBE_FAILED" -> {
                if (fa) "هیچ سروری به تست پینگ پاسخ نداد. حالت بایپس یا شبکه را بررسی کنید." else "No server responded to probe. Check network or bypass mode."
            }
            code == "SAVE_FAILED" -> {
                if (fa) "ذخیره اطلاعات در حافظه امن ناموفق بود." else "Failed to save in secure vault."
            }
            code == "NO_INTENT_APP" -> {
                if (fa) "برنامه‌ای برای باز کردن این بخش روی دستگاه یافت نشد." else "No compatible app found to open this link."
            }
            code == "NO_PROFILES" -> {
                if (fa) "هیچ پروفایل سروری یافت نشد." else "No server profiles available."
            }
            else -> errorMessage(code).ifEmpty { code }
        }
    }

    fun errorMessage(code: String?): String = when (code) {
        null -> ""
        "AUTH_FAILED" -> if (fa) "نام کاربری یا رمز عبور TunnelBear نادرست است." else "TunnelBear authentication failed."
        "ACCOUNT_REQUIRED" -> if (fa) "لطفاً ابتدا نام کاربری و رمز عبور TunnelBear را وارد کنید." else "Please configure your TunnelBear credentials first."
        "CONNECT_DEADLINE", "CONNECTION_TIMEOUT" -> if (fa) {
            "زمان اتصال به پایان رسید. ابتدا پینگ بگیرید یا سرور دیگری انتخاب کنید."
        } else {
            "Connection timed out. Ping servers or switch to another node."
        }
        "NO_DATA" -> if (fa) {
            "تونل برقرار شد اما دیتای اینترنت عبور نکرد (DPI). سرور دیگری تست کنید."
        } else {
            "Tunnel opened but traffic was blocked. Try another server."
        }
        "CORE_UNAVAILABLE" -> if (fa) "کتابخانه بومی OpenVPN بارگذاری نشد." else "Native OpenVPN library unavailable."
        "VPN_DENIED" -> if (fa) "مجوز ساخت شبکه VPN توسط سیستم صادر نشد." else "Android VPN permission denied."
        else -> if (fa) "خطای اتصال ($code)" else "Connection error ($code)"
    }

    companion object {
        fun of(lang: AppLanguage) = QobearStrings(lang == AppLanguage.FA)
    }
}
