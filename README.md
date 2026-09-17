# CarGenome

Android-приложение для учёта обслуживания автомобиля: заправки, пробег, регламент ТО,
расходы и распознавание машины по VIN.

## Требования

| Компонент | Версия |
| --- | --- |
| Android Studio | 2026.1.4 или новее |
| JDK | 25 (встроенный JBR из Android Studio) |
| Gradle | 9.7.1 (через wrapper, скачивается автоматически) |
| Android Gradle Plugin | 9.4.0 |
| Kotlin | 2.4.20 |
| SDK Platform | Android 17 (API 37) для компиляции, Android 16 (API 36) как target |

В SDK Manager должны стоять:

- SDK Platforms: `Android 17.0 (CinnamonBun)` API 37.0 и `Android 16.0 (Baklava)` API 36.0
- SDK Tools: Android SDK Build-Tools, Android SDK Platform-Tools

## Первый запуск в Android Studio

1. `File → Open` и выбрать корень репозитория.
2. `Settings → Build, Execution, Deployment → Build Tools → Gradle` → **Gradle JDK: Embedded JBR**.
   Системная Oracle JDK не подходит: AGP её не поддерживает.
3. Дождаться Gradle sync и запустить конфигурацию `app`.

## Сборка из консоли

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug
```

Полезные задачи:

```powershell
.\gradlew.bat :core:vin:test        # тесты VIN-декодера
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleRelease
```

## Про версии SDK

`compileSdk = 37`, `targetSdk = 36`. Свежие AndroidX-библиотеки требуют компиляции
против API 37, при этом `targetSdk = 36` сохраняет поведение приложения как у
Android 16. `minSdk = 26` — Android 8.0 и новее.

Нативных библиотек в проекте нет, поэтому отдельная сборка под x86_64 или arm64
не нужна: APK работает на всех 64-битных устройствах.

## Структура

```
core/vin   Чистый Kotlin/JVM модуль: разбор VIN без сети и без Android API
app        Compose UI, Room, сеть, DI, WorkManager
tools      Python-скрипты, которые собирают таблицу WMI
```

## Распознавание VIN

Гибридная схема:

1. **Офлайн** — `core/vin` всегда разбирает VIN сам: страна, производитель по WMI,
   год выпуска, код завода, серийный номер, проверка контрольной цифры.
   Таблица WMI лежит в `app/src/main/assets/wmi.json`.
2. **Онлайн** — бесплатный [NHTSA vPIC](https://vpic.nhtsa.dot.gov/api/) без ключа и
   лимитов дополняет результат моделью, кузовом, двигателем и типом топлива.
   Ответ кэшируется в базе, поэтому повторный разбор работает без сети.

Ограничение vPIC: подробные данные есть в основном для машин рынка США. Для
европейских авто обычно возвращается только марка и завод, для российских
(например, `XTA...`) — ничего. Это и закрывает офлайн-таблица WMI.

### Что именно разбирается офлайн

| Что | Откуда | Оговорки |
| --- | --- | --- |
| Страна и регион | позиции 1–2 по таблице ISO 3780 | ISO оставил незанятыми `X2`–`X0`, хотя `X7L` (Renault Россия) и `X96` (ГАЗ) реально выпускаются. Там страна берётся из таблицы WMI |
| Производитель и марка | позиции 1–3, для мелкосерийных ещё 12–14 | — |
| Модельный год | позиция 10 | Код повторяется каждые 30 лет. В Северной Америке цикл снимается по позиции 7 (49 CFR 565.15), в остальном мире остаётся неоднозначность, а часто года там вообще нет |
| Контрольная цифра | позиция 9 | Обязательна только в Северной Америке и Китае. В Европе и Японии несовпадение — это норма, поэтому оно показывается предупреждением, а не ошибкой |

### Таблица WMI

`app/src/main/assets/wmi.json` — около 4600 записей, собранных из трёх источников:

1. Список WMI из английской статьи о VIN (опирается на реестр немецкого
   Kraftfahrt-Bundesamt) — единственный источник, где есть марки без продаж в
   США: `TMB` Škoda, `VSS` SEAT, `SJN` Nissan Sunderland.
2. NHTSA vPIC — всё, что зарегистрировано для продажи в США, с самыми
   аккуратными названиями марок.
3. `tools/wmi_supplement.json` — постсоветские заводы из русской статьи о VIN и
   приказа Минпромторга РФ от 14.01.2010 N 9.

Пересборка (каждый шаг пишет свой промежуточный файл, поэтому их можно запускать
по отдельности):

```powershell
python tools\extract_wiki_wmi.py      # -> tools/wmi_wikipedia.json
python tools\extract_cis_wmi.py       # -> tools/wmi_supplement.json
python tools\generate_wmi_dataset.py  # -> app/src/main/assets/wmi.json
python tools\check_wmi_coverage.py    # быстрая проверка ключевых кодов
```

vPIC блокирует клиента, который запрашивает слишком часто, поэтому его ответы
кэшируются в `tools/vpic_wmi_cache.json` и переиспользуются. Прицепы в датасет не
попадают: их в vPIC 9600 из 13000 записей, а приложение про автомобили.

Регрессию датасета ловит `WmiDatasetTest` в `core/vin` — он читает тот же самый
файл, что уезжает в APK.

## Проверка на устройстве

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
.\gradlew.bat :app:assembleDebug
& $adb install -r -t app\build\outputs\apk\debug\app-debug.apk
& $adb shell am start -n com.cargenome.app.debug/com.cargenome.app.MainActivity
```

В debug-сборку подключён LeakCanary, поэтому утечки видны в логе:

```powershell
& $adb logcat -d | Select-String "LeakCanary"
& $adb shell dumpsys meminfo com.cargenome.app.debug | Select-String "Activities:|Java Heap:|TOTAL PSS"
```

`Activities:` должно оставаться равным 1 после серии поворотов экрана — это
самая простая проверка того, что Activity не удерживается.

## Подпись релиза

Создайте `keystore.properties` в корне проекта (файл в `.gitignore`):

```properties
storeFile=cargenome-release.jks
storePassword=...
keyAlias=cargenome
keyPassword=...
```

Без этого файла `assembleRelease` собирает неподписанный APK.
