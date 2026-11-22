# Установка Grok Wear 0.8.4 (внешний прокси Watch VPN)

Этот вариант **не** содержит mihomo. Весь трафик Grok идёт через **Watch VPN** (`127.0.0.1:7890`, HTTP CONNECT). Без Watch VPN приложение **не** ходит в интернет напрямую.

> Неофициальный клиент Grok. API может измениться или сломаться. Не аффилирован с xAI или Cursor.

**Релиз:** https://github.com/aquanetworks/grok-wear/releases/tag/grok-wear-0.8.4-ext

**APK:** https://github.com/aquanetworks/grok-wear/releases/download/grok-wear-0.8.4-ext/grok-wear-armeabi-v7a.apk  
`si.zya.grokwear`, versionCode **48**, 0.8.4, armeabi-v7a, без `libmihomo.so`. Та же sideload-подпись, что 0.8.0-vless / 0.8.3-ext — ставится поверх.

Сначала поставьте **Watch VPN 0.3.1**:
https://github.com/aquanetworks/grok-wear/releases/download/watch-vpn-0.3.1/watch-vpn-armeabi-v7a.apk

## 1. Режим разработчика на часах

1. **Настройки → О часах → Сведения о ПО**.
2. Нажмите **Номер сборки** семь раз.
3. **Параметры разработчика**: **Отладка по ADB**, **беспроводная отладка**.

## 2. Установка через WearLoad

1. Watch VPN 0.3.1 (если ещё нет).
2. Grok Wear `grok-wear-armeabi-v7a.apk` поверх существующей (`adb install -r --no-streaming`).
3. Если `INSTALL_FAILED_UPDATE_INCOMPATIBLE` — другая подпись: удалите `si.zya.grokwear` и поставьте снова (вход сбросится).

## 3. Первый запуск

1. Откройте **Watch VPN** → grant (один раз, aShell You, **без** `adb shell`):

```
pm grant com.aquanetworks.watchvpn android.permission.WRITE_SECURE_SETTINGS
```

2. Watch VPN → сервер → **Подключить**. Дождитесь «Прокси 127.0.0.1:7890».
3. Откройте **Grok Wear**. Строка: **Прокси: OK (127.0.0.1:7890)**.
4. **Sign in** — QR на телефоне, как в обычном Grok Wear.
5. Если прокси выключен: «Прокси не запущен. Откройте Watch VPN и нажмите Подключить.» → кнопка открывает Watch VPN.

## 4. Проверка (aShell You, без префикса `adb shell`)

```
logcat -d -s grokwear-proxy:* | tail -40
```

Ожидается: `CONNECT api.x.ai via HTTP 127.0.0.1:7890` / `OK api.x.ai`. Токенов в логе нет.

Дополнительно:

```
netstat -n | grep 7890
settings get global http_proxy
```

Watch VPN сам ставит `http_proxy`; Grok Wear на системный ProxySelector **не** опирается.

Снять прокси часов (если нет интернета):

```
settings put global http_proxy :0
```

## 5. Настройки прокси в Grok Wear

Экран **Прокси**: адрес/порт (по умолчанию `127.0.0.1:7890`), **Проверить** (GET `https://api.x.ai/` через CONNECT — HTTP-код и мс), **Открыть Watch VPN**.

## 6. Кнопка Home

В **Настройки → Кнопка Home** выберите бота, которого вызывает удержание Home / ассистента. «Основной бот (по умолчанию)» следует за Primary в аккаунте. Выбор хранится только на часах. С карточки бота: **Назначить на кнопку Home**.

## 7. Кнопка наушников (Bluetooth)

Наушники должны быть **подключены к часам**, не к телефону. Иначе кнопка уйдёт в ассистент телефона.

**Galaxy Buds Pro:** в Galaxy Wearable для **этих часов** удержание (touch-and-hold) = **Voice command** / «Голосовая команда», не Bixby и не перевод.

**Shokz:** action button на часах — система обычно шлёт `VOICE_COMMAND`, если Grok Wear выбран цифровым ассистентом.

После нажатия:

```
logcat -d -s grokwear-assist:* | tail -20
```

Ожидается строка с `action=` (`android.intent.action.VOICE_COMMAND`, `ASSIST`, `VOICE_ASSIST` или `android.speech.action.VOICE_SEARCH_HANDS_FREE`) и `source=` (например `activity`, `keyguard`, `voice-session`, `mediasession`).

### Что гарантировано (если система доставила intent)

- `android.intent.action.VOICE_COMMAND` (классическая кнопка BT-гарнитуры, `CATEGORY_DEFAULT`)
- `android.speech.action.VOICE_SEARCH_HANDS_FREE`
- `ACTION_ASSIST` / `VOICE_ASSIST` (кнопка Home / ассистент часов)
- Экран блокировки: `VoiceInteractionService.onLaunchVoiceAssistFromKeyguard`, если Grok Wear — цифровой ассистент

Все эти пути вызывают **того же бота**, что **Кнопка Home** (иначе Primary).

### Лучшее усилие (opt-in)

**Настройки → Кнопка наушников** (по умолчанию **Выкл**). Включает MediaSession (FGS, `setActive(true)`, PlaybackState PLAYING с `ACTION_PLAY|PAUSE|PLAY_PAUSE`). Короткое нажатие любого из:

- `KEYCODE_MEDIA_PLAY` (126) — так приходит кнопка на проверенных Shokz/Buds
- `KEYCODE_MEDIA_PLAY_PAUSE` (85)
- `KEYCODE_HEADSETHOOK` (79)
- `KEYCODE_MEDIA_PAUSE` (127)
- `KEYCODE_VOICE_ASSIST` (231)

срабатывает на **первом** DOWN или UP; парное событие в 1.5 с — `debounced`. Если играет другая музыка (`isMusicActive` / чужой MediaController) — `passthrough music-active`, ключ не глотаем. Во время звонка Grok: **Во время звонка** = `завершить звонок` (по умолчанию) или `микрофон`. После звонка MediaSession заново захватывается.

Ожидаемые строки `grokwear-assist`: `launch`, `passthrough music-active`, `debounced`, `end-call`.

Экран выключен: FGS + MediaSession должны получать кнопки без открытого UI; **в этой среде экран часов не проверялся**.

AT+BVRA (HFP) на Wear OS обычно превращается в `VOICE_COMMAND` или в media-key; Samsung/Google могут перехватить BVRA раньше стороннего приложения.

### Не проверено на часах

- Экран выключен (screen off) для MediaSession
- Доставка AT+BVRA в стороннее приложение на One UI Watch
- Совместная работа с активным плеером при включённой «Кнопка наушников»
- Hands-free `VOICE_SEARCH_HANDS_FREE` с конкретной гарнитуры

## Если не входит

| Симптом | Что делать |
|---|---|
| «Прокси не запущен…» | Watch VPN → Подключить, подождать до 30 с |
| Прокси: нет соединения | `logcat -d -s mihomo:* watchvpn:* grokwear-proxy:* \| tail -80` |
| Watch VPN не открывается | Приложение не установлено |
| Голос/чат после OK | Смотрите `grokwear-proxy` — хост и FAIL/OK |
