# معماری هوش مصنوعی و موتور اجرای فرمان FRIDAY AI

## اصل اساسی
مدل زبانی مسئول فهم، استدلال و برنامه‌ریزی است؛ اما حق اجرای مستقیم کد یا عملیات دستگاه را ندارد. تمام اقدامات از Action Registry محدود، اعتبارسنجی محلی و permission gate عبور می‌کنند.

جریان: User Input → Context Builder → AI Gateway → Intent/Tool Planner → Policy Gate → Confirmation → Android Action Registry → Result → Assistant Response.

## اجزای معماری
### Android Client
- Chat UI و Voice UI
- ChatRepository و مدیریت پاسخ جریانی
- DeviceActionRegistry با allowlist
- PermissionCoordinator برای درخواست مجوز در لحظه نیاز
- ActionApprovalStore برای اتصال تأیید به ActionPlan
- LocalDataRepository برای مکالمه، note و task
- ActionResultReporter برای گزارش نتیجه واقعی
- PrivacyController برای حافظه، فایل و میکروفون

### AI Gateway
- اعتبارسنجی نشست و کاربر
- rate limit و quota
- انتخاب provider و model
- ساخت context با حداقل داده لازم
- فراخوانی مدل و تبدیل پیشنهاد آن به ActionPlan
- schema validation و خطاهای استاندارد
- usage و latency حداقلی
- مخفی‌سازی secrets

### Provider Adapter
قرارداد مشترک برای generate، stream، health، capabilities و usage estimate. Providerها شامل Cloudflare Workers AI و Ollama هستند و افزودن ارائه‌دهنده جدید نباید به تغییر UI نیاز داشته باشد.

### Policy Engine
اعتبارسنجی allowlist، schema، permission، precondition، risk، confirmation و قابلیت لغو. مدل حق تعیین یا کاهش سطح ریسک را ندارد.

## ActionPlan
ساختار منطقی:
{
  "id": "uuid",
  "tool": "reminder.create",
  "schema_version": 1,
  "arguments": {"title": "یادآوری تماس", "due_at": "ISO-8601"},
  "risk": "low",
  "requires_confirmation": true,
  "preconditions": ["notification_permission"],
  "summary": "ایجاد یادآور برای فردا ساعت ۱۰",
  "expires_at": "timestamp"
}

این نمونه قرارداد طراحی است، نه دستور اجرایی. سرور و کلاینت هر دو باید schema را مستقل اعتبارسنجی کنند.

## سطح ریسک
- R0: خواندنی و کم‌خطر؛ مانند ساعت و محاسبه
- R1: تغییر محلی قابل برگشت؛ مانند ساخت پیش‌نویس note یا task
- R2: عملیات ارتباطی یا تغییر خارجی؛ مانند تماس، پیام، event یا share؛ تأیید صریح لازم
- R3: مالی، هویتی، حریم خصوصی یا برگشت‌ناپذیر؛ در نسخه اول اجرا نشود

Policy در کد و سرور تعیین می‌شود، نه از خروجی مدل.

## قاعده تأیید
- هر ActionPlan شناسه و expiry یکتا دارد.
- تأیید فقط برای همان tool و همان arguments معتبر است.
- تغییر پارامتر، تأیید قبلی را باطل می‌کند.
- cancel اجرای Plan را غیرممکن می‌کند.
- عملیات حساس باید مقصد و محتوای قابل ارسال را پیش از تأیید نشان دهد.
- تأیید مبهم یا صوت نامطمئن نباید اجرا شود.

## API پیشنهادی
قرارداد قدیمی POST /v1/chat تا پایان مهاجرت حفظ شود.

Endpoint جدید: POST /v1/assistant/turn

درخواست مفهومی:
{
  "conversation_id": "uuid",
  "message": {"type": "text", "text": "برای فردا ساعت ۱۰ یادآور تماس بساز"},
  "client_capabilities": ["reminder.create", "app.open"],
  "locale": "fa-IR",
  "context": {"timezone": "Europe/Paris"}
}

پاسخ مفهومی:
{
  "conversation_id": "uuid",
  "message": {"text": "یادآور را برای فردا ساعت ۱۰ آماده کردم."},
  "action_plan": {
    "id": "uuid",
    "tool": "reminder.create",
    "arguments": {"title": "تماس", "due_at": "ISO-8601"},
    "risk": "low",
    "requires_confirmation": true,
    "summary": "ایجاد یادآور برای فردا ساعت ۱۰"
  },
  "sources": [],
  "usage": {"provider": "provider-id", "model": "model-id"}
}

پس از اجرا می‌توان نتیجه را با POST /v1/actions/{action_id}/result گزارش کرد. نتیجه حداقلی شامل status، tool، executed_at و error_code باشد؛ داده دستگاه غیرضروری ارسال نشود.

## پاسخ جریانی
- SSE یا پروتکل معادل
- eventهای meta، text_delta، action_proposal، source، error و done
- شناسه درخواست برای هر stream
- cancellation در کلاینت و Provider در حد پشتیبانی
- متن stream به‌تنهایی اجازه اجرا نیست؛ ActionPlan کامل، معتبر و تأییدشده لازم است
- fallback به JSON معمولی

## مدیریت context
- پیام‌های مرتبط مکالمه جاری
- خلاصه پیام‌های قدیمی برای کنترل طول context
- حافظه بلندمدت فقط با رضایت
- اطلاعات حساس بدون ضرورت وارد prompt نشود
- خروجی ابزار با provenance جدا شود
- صفحه وب، فایل و متن کاربر هیچ‌گاه system prompt نیستند

## داده‌ها و نگهداری
مدل داده پیشنهادی: User، Session، Conversation، Message، MemoryItem، ActionAudit، UserSettings و UsageLog. زمان نگهداری و حذف باید مستند باشد. متن کامل پیام‌ها در analytics و log عادی ذخیره نشود.

## امنیت
- HTTPS اجباری
- authentication و authorization
- token کوتاه‌عمر و قابل لغو
- rate limit per user و دفاع تکمیلی per IP
- محدودیت اندازه و timeout
- JSON schema validation پیش و پس از مدل
- secret manager
- عدم اتکا به CORS برای امنیت اپ Native
- log بدون token، secret و متن کامل مکالمه
- محافظت در برابر prompt injection، SSRF و ابزار غیرمجاز
- endpoint مدیریت فقط برای مدیر احراز هویت‌شده

## کدهای خطا
AUTH_REQUIRED، SESSION_EXPIRED، RATE_LIMITED، QUOTA_EXCEEDED، PROVIDER_UNAVAILABLE، INVALID_REQUEST، INVALID_ACTION_PLAN، ACTION_NOT_SUPPORTED، PERMISSION_REQUIRED، USER_CANCELLED، NETWORK_TIMEOUT و INTERNAL_ERROR.

## تست‌های حیاتی
ActionPlan معتبر/نامعتبر، پارامتر اضافی، ابزار خارج allowlist، تأیید منقضی، تغییر پارامتر پس از تأیید، permission ردشده، لغو عملیات، provider unavailable، timeout، stream قطع‌شده، rate limit و prompt injection داخل PDF یا صفحه وب.
