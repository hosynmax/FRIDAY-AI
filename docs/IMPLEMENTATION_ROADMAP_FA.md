# نقشه راه پیاده‌سازی FRIDAY AI

توسعه بر اساس معیار پذیرش است. هیچ فاز بعدی نباید امنیت یا تست فاز قبلی را دور بزند.

## فاز ۰ — ممیزی
- بررسی Android، Backend، Cloudflare، D1، workflowها و CI
- اجرای Build و تست پایه و ثبت log
- ثبت قرارداد /v1/chat و محدودیت‌های فعلی
- inventory قابلیت‌های واقعی و نمایشی
- ثبت ریسک‌های امنیتی و نیازهای استقرار

پایان فاز: گزارش ممیزی، وضعیت Build واقعی و backlog اولویت‌دار.

## فاز ۱ — پایه Android
- تفکیک MainActivity به screen و ViewModel
- navigation و design tokens
- Room برای conversation/message/task/note
- DataStore برای تنظیمات
- شبکه با timeout، cancellation و error mapping
- StateFlow و state immutable
- RTL، safe insets و صفحه adaptive

پایان فاز: چت و تنظیمات بدون regression؛ history پس از restart باقی بماند؛ تست Repository و ViewModel.

## فاز ۲ — مکالمه کامل
- پاسخ جریانی و Stop/Retry
- تاریخچه چند مکالمه و جست‌وجو
- push-to-talk و TTS
- انتخاب زبان و سرعت صوت
- فایل با Storage Access Framework
- خطای شبکه و حالت offline

پایان فاز: پاسخ stream قابل لغو، history قابل بازیابی و مجوز میکروفون فقط هنگام نیاز.

## فاز ۳ — AI Gateway
- provider adapter و model registry
- حفظ /v1/chat و افزودن API نسخه‌دار
- authentication و token lifecycle
- rate limit، quota و health endpoint
- prompt configuration و audit
- error mapping و telemetry حداقلی

پایان فاز: Provider قابل تعویض، endpoint امن و تست قرارداد.

## فاز ۴ — موتور فرمان
- ActionPlan و JSON schema
- Action Registry و allowlist
- PermissionCoordinator و capability detection
- preview، confirm و cancel
- ActionResult واقعی
- تست plan منقضی، تأیید نادرست و پارامتر تغییرکرده

ترتیب ابزارهای MVP: زمان، تاریخ، ماشین‌حساب، بازکردن برنامه، تنظیمات مجاز، تایمر، یادداشت، task و یادآور.

پایان فاز: هیچ action خارج Registry اجرا نشود و action حساس بدون تأیید موفق نشود.

## فاز ۵ — وب و فایل
- جست‌وجوی وب سمت سرور
- نمایش عنوان، دامنه، تاریخ و منبع
- خلاصه‌سازی مستند
- parsing PDF متنی و فایل مجاز
- اطلاع‌رسانی پیش از upload
- حفاظت در برابر prompt injection

پایان فاز: منبع جعلی نداشته باشیم و پردازش فایل شفاف و قابل لغو باشد.

## فاز ۶ — بهره‌وری و حافظه
- note، task، reminder و calendar
- routine builder با زمان‌بندی قابل مشاهده و توقف
- حافظه opt-in و مدیریت کامل
- export/delete
- خلاصه context و retrieval محدود

پایان فاز: حافظه قابل خاموش و حذف باشد و scheduleها تکراری یا بی‌صاحب نشوند.

## فاز ۷ — انتشار
- unit، integration، instrumentation و contract tests
- lint، dependency review و secret scanning
- بررسی عملکرد، battery و شبکه
- تست Android 13+ روی emulator و دستگاه واقعی در صورت دسترسی
- release signing با GitHub Actions Secrets
- تولید APK/AAB، بررسی package و signature
- release notes، artifact و checksum

پایان فاز: CI موفق، APK بررسی‌شده و گزارش صادقانه از قابلیت‌های باقیمانده.

## MVP اولویت‌دار
- چت فارسی/انگلیسی با Backend
- history محلی و چند مکالمه
- ورودی صوتی و TTS
- وضعیت سرور و online/offline
- ابزار calculator، time/date، timer، note و task
- ActionPlan و تأیید برای reminder
- تنظیمات زبان، صدا و حریم خصوصی
- تست CI

## نسخه‌های بعدی
وب‌گردی منبع‌دار، تحلیل PDF، حافظه بلندمدت، تقویم پیشرفته، routine، حساب و sync ابری، مدل قوی‌تر روی سرور GPU و wake word اختیاری.

## گزارش هر فاز
دامنه تغییر، فایل‌ها، commit، معیار پذیرش، تست‌های واقعاً اجراشده، نتیجه، ریسک، وابستگی بیرونی و تصمیم‌های باز را ثبت کن.
