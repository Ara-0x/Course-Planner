# Course Planner — برنامه‌ریز دروس دانشگاه (آفلاین)

اپلیکیشن اندرویدی **آفلاین** برای مدیریت دروس، تشخیص تداخل کلاسی/امتحانی و تولید خودکار
برنامه هفتگی بدون تداخل. رابط کاربری کاملاً فارسی و راست‌چین با Jetpack Compose.

- Package: `ir.courseplanner.app`
- minSdk 24 / targetSdk 36 / Java 17 / Kotlin + Compose Material3
- دیتابیس: Room (نسخه ۳) — آفلاین-first، بدون بک‌اند

## وضعیت بیلد و توسعه (Build & Status)

- [x] مهاجرت کامل پکیج به `ir.courseplanner.app`
- [x] ارتقا به Java 17 و Kotlin 2.2.10 و AGP 9.1.1
- [x] پیاده‌سازی زیرساخت آفلاین (Hilt + Room + DataStore)
- [x] کامپایل موفق و تولید **Debug APK** در `app/build/outputs/apk/debug/app-debug.apk`
- [x] رفع تمامی اخطارهای کامپایلر (Deprecations) و مدرن‌سازی کامپوننت‌های Jetpack Compose (استفاده از آیکون‌های AutoMirrored و MenuAnchorType)

## امکانات فعلی

- تعریف درس، گروه (سکشن)، جلسات هفتگی (شنبه تا جمعه + پشتیبانی از هفته زوج/فرد)، ساعت امتحان
- تشخیص تداخل: هم‌پوشانی ساعت کلاس (با احترام به مرز چسبیده = بدون تداخل)، تداخل روز/ساعت امتحان
- برنامه‌ساز خودکار (backtracking تا ۱۰۰ ترکیب) + رتبه‌بندی با ۴ سلیقه:
  `توازن کلی / حداقل گپ / فشرده‌ترین روزها / بدون ۸ صبح`
- ایمپورت CSV و JSON با ولیدیشن فارسی، دیتای نمونه دانشگاهی
- خروجی متنی فارسی + کپی در کلیپ‌بورد + اشتراک‌گذاری
- آرشیو جزوات به تفکیک درس و دسته‌بندی + بوکمارک
- تم روشن/تاریک + ۷ تم رنگی، فونت وزیرمتن، چگالی جدول زمانی

## ساختار پروژه

```
app/src/main/java/ir/courseplanner/app/
├── MainActivity.kt                  # هاست Compose + باتم‌نویگیشن (خانه/دروس/برنامه‌ساز/جزوات/تنظیمات)
├── data/
│   ├── model/                       # Course, CourseSection, ClassSession, CourseDocument, ...
│   ├── local/                       # Room: AppDatabase, CourseDao, SectionDao, CourseDocumentDao
│   ├── repository/CourseRepository.kt
│   ├── importer/CourseImporter.kt   # پارس CSV/JSON + نمونه + اکسپورت JSON
│   └── preferences/AppPreferences.kt
├── engine/ScheduleEngine.kt         # قلب منطق: تداخل، تولید ترکیب، امتیازدهی
├── ui/
│   ├── CoursePlannerViewModel.kt
│   ├── screens/  (Home, Courses, Schedule, Documents, Settings)
│   └── components/ (WeeklyTimetable, AddCourseDialog, ...)
└── util/TimetableExporter.kt
```

## اجرا

1. پروژه را در Android Studio (Koala به بعد، JDK 17) باز کنید.
2. Gradle Sync بگیرید — نیازی به `google-services.json` یا کلید API نیست.
3. Run روی امولاتور/گوشی (API 24+).
4. تست‌ها: `ScheduleEngineTest` (تداخل، هفته زوج/فرد، ژنراتور، ایمپورتر) +
   `TimetableExporterTest` — از مسیر `app/src/test`.

```bash
./gradlew :app:testDebugUnitTest
```

## شاخه‌ها

- `master` — نسخه پایدار (ایمپورت اولیه پروتوتایپ)
- `develop` — شاخه فعال توسعه (فاز ۰ روی همین شاخه است)

## فاز ۰ — چه چیزی تمیز شد؟

- [x] کامیت اولیه + شاخه `develop`
- [x] تغییر پکیج `com.example` و `com.aistudio...` به `ir.courseplanner.app`
- [x] ارتقای Java 11 به 17
- [x] حذف وابستگی‌های بلااستفاده (Firebase AI/AppCheck، Retrofit، OkHttp، Moshi، Secrets)
      چون هیچ کدی از آن‌ها استفاده نمی‌کرد و اپ آفلاین است
- [x] حذف `metadata.json` و `.env.example` مخصوص AI Studio
- [x] قوانین ProGuard برای Room (آماده‌سازی فعال‌سازی minify در آینده)

## نقشه راه پیشنهادی

- فاز ۱ (زیرساخت آفلاین — انجام شد): Hilt، DataStore به‌جای SharedPreferences
  (با `SharedPreferencesMigration` برای حفظ تنظیمات نصب‌های قبلی)، حذف
  `fallbackToDestructiveMigration` و اجباری شدن Migration برای هر تغییر اسکیما
  (اسکیماها در `app/schemas` ورژن می‌شوند)
- بعدی: Navigation-Compose، سقف واحد در ژنراتور، یادآور لوکال کلاس/امتحان،
  خروجی ICS، بکاپ/ری‌استور فایل JSON

## لایسنس

به‌زودی مشخص می‌شود (پیشنهاد: Apache-2.0 یا MIT).
