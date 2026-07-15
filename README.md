# Сканер документов AI: PDF

Android-приложение для сканирования бумажных документов: съёмка, автоматическое
и ручное выделение границ, исправление перспективы, фильтры, многостраничный PDF,
OCR и AI-анализ текста. Работает **без Google Play Services**, целевой магазин — RuStore.

## Стек

Kotlin · Jetpack Compose · Material 3 · CameraX · Room · DataStore · Koin ·
Ktor · kotlinx.serialization · Coroutines/Flow · PdfDocument (без GMS).

## Сборка

Требуется Android Studio (Ladybug+) или локальный Android SDK (compileSdk 35, JDK 17+).

```bash
./gradlew clean
./gradlew test                       # unit-тесты (JVM)
./gradlew :composeApp:assembleDebug  # APK: composeApp/build/outputs/apk/debug/
```

> Wrapper-jar включён в репозиторий; при проблемах выполните `gradle wrapper --gradle-version 8.11.1`.

## Архитектура

Один Android-модуль `composeApp`, Clean Architecture:

```
ru.aiscanner.docs/
    core/            AppResult/AppError, диспетчеры, DI (Koin)
    domain/
        model/       Document, DocumentPage, CropCorners, AiSummary, ...
        geometry/    QuadValidator — валидация четырёхугольника обрезки
        logic/       PdfFileNameGenerator, FreePlanLimiter
        repository/  интерфейсы 7 репозиториев
        usecase/     Create/AddPage/Reorder/Export/Ocr/Ai...
    data/
        db/          Room (documents, pages)
        files/       documents/{id}/{original,processed,previews,export}
        imageprocessing/  DocumentImageProcessor, perspective transform,
                          DocumentCornerDetector (OpenCV подключается здесь)
        ocr/         OcrEngine (заглушка; PaddleOCR подключается без переделок)
        ai/          AiDocumentService: Mock + Ktor-клиент backend-прокси
        export/      PdfExporter (PdfDocument, A4/авто, поля, качество)
        analytics/   Analytics + debug-реализация (без содержимого документов)
    presentation/    Compose-экраны + ViewModel (UiState/UiEffect):
                     Home, Camera, Crop, PageEditor, Document, Ocr, Settings, Premium
```

Ключевые решения:
- **Неразрушающая обработка**: хранится оригинал + параметры (crop/rotation/filter/
  brightness/contrast); превью фильтров — через `ColorFilter.colorMatrix` без
  пересоздания JPEG; файл рендерится один раз при сохранении.
- **Координаты обрезки** — нормализованные (0..1) относительно исходного изображения.
- **Перспектива** — `Matrix.setPolyToPoly` (проективное преобразование по 4 точкам);
  детектор границ за интерфейсом `DocumentCornerDetector` (сейчас fallback с отступами,
  OpenCV Canny→контуры — Этап 3).
- **Приватность**: всё локально; AI получает только распознанный текст после
  явного согласия; бэкап документов отключён; API-ключи в APK не хранятся
  (backend-прокси, `AI_BASE_URL` в BuildConfig).
- **Монетизация**: `FreePlanLimits` конфигурируемы (PDF ≤ 5 стр., 3 OCR, 1 AI);
  `SubscriptionRepository` — заглушка до RuStore Billing (Этап 8).

## Тесты

`composeApp/src/test`: QuadValidator, PdfFileNameGenerator, FreePlanLimiter,
use cases (create/reorder/delete, фейковый репозиторий), AiResponseParser,
StubSubscriptionRepository, HomeViewModel (StandardTestDispatcher).

## Статус этапов ТЗ

| Этап | Статус |
|---|---|
| 1. Каркас (Gradle, Compose, DI, Room, навигация, главный экран) | ✅ |
| 2. Камера (CameraX, разрешения, съёмка, вспышка) | ✅ |
| 3. Обнаружение документа | Ручная коррекция + перспектива ✅; OpenCV-детектор — интерфейс готов |
| 4. Фильтры и страницы | ✅ (reorder кнопками, drag-and-drop позже) |
| 5. PDF (A4/авто, поля, качество, share) | ✅ |
| 6. OCR | Экран/прогресс/отмена/редактирование ✅; движок — заглушка `OcrEngine` |
| 7. AI | Mock + Ktor-клиент прокси, согласие, дисклеймер, лимиты ✅ |
| 8. RuStore Billing | Заглушка за интерфейсом |
| 9. Релиз | R8-конфиг есть; иконка/сплэш/проверки — далее |
