# Установка Watch VPN (системный HTTP-прокси на часах)

Отдельное приложение **Watch VPN** (`com.aquanetworks.watchvpn`) поднимает **mihomo** как локальный mixed HTTP/SOCKS на **`127.0.0.1:7890`**. Системный `http_proxy` направляет туда приложения, которые его уважают.

Это **не TUN / не VpnService**. На Galaxy Watch Ultra (Android 16) служба `IVpnManager` отсутствует: `establish()` падает с `NullPointerException`. Корня и Shizuku нет.

**WhatsApp, Telegram и многие мессенджеры игнорируют системный HTTP-прокси** — их трафик **не** пойдёт через узел. Браузер и приложения, которые читают `http_proxy`, — пойдут.

Это **не** Grok Wear. Grok Wear остаётся отдельным APK.

**APK:** `watch-vpn-armeabi-v7a.apk` (версия **0.2.0**, ~76 МБ из‑за mihomo `armeabi-v7a`, `compileSdk`/`targetSdk` 36). Подпись — тот же sideload-ключ, что и Grok Wear, см. [Подпись](#подпись-apk).

> Боевую ссылку подписки в репозиторий и в чаты не кладите.

## 1. Режим разработчика на часах

1. **Настройки → О часах → Сведения о ПО**.
2. Нажмите **Номер сборки** семь раз.
3. **Параметры разработчика**:
   - **Отладка по ADB**;
   - **Отладка по Wi‑Fi** / **Беспроводная отладка**.

Оставьте экран часов включённым: в сон Wi‑Fi часто гаснет.

## 2. Установка через WearLoad

1. WearLoad (или Wear Installer 2 / Bugjaeger) на телефоне в той же Wi‑Fi сети.
2. На часах: беспроводная отладка → **Сопряжение с устройством** (IP, порт, шестизначный код).
3. Сопряжение, затем подключение к **рабочему** порту ADB.
4. Установите `watch-vpn-armeabi-v7a.apk`. Дождитесь Success.
5. Если стоит старая сборка с **другой** подписью: `adb uninstall com.aquanetworks.watchvpn` и снова install. Та же подпись (0.1.x → 0.2.0): достаточно `-r`.

## 3. Установка с компьютера (ADB Wi‑Fi)

```sh
adb pair <IP>:<порт-сопряжения>
adb connect <IP>:<рабочий-порт>
adb install -r --no-streaming watch-vpn-armeabi-v7a.apk
```

`--no-streaming` надёжнее на часовом Wi‑Fi.

## 4. Один раз в aShell You (без префикса `adb shell`)

Порядок. Команды в **aShell You** на часах (или в `adb shell`, но тогда без этого префикса в самой оболочке):

```
pm grant com.aquanetworks.watchvpn android.permission.WRITE_SECURE_SETTINGS
```

После этого **Подключить** в приложении само ставит `http_proxy` на `127.0.0.1:7890`, **Отключить** / падение mihomo / краш / перезагрузка без автоподключения — снимает (`:0`). Так часы не остаются с мёртвым прокси и без интернета.

Если grant не делали — после каждого Connect вручную:

```
settings put global http_proxy 127.0.0.1:7890
```

Снять (всегда можно, если нет сети):

```
settings put global http_proxy :0
```

Те же строки показаны в приложении: **Справка / UA**.

## 5. Подписка и сервер

1. Откройте **Watch VPN**.
2. **Подписка** — введите HTTPS-ссылку v2RayTun (клавиатура / диктовка Wear). Подойдут Clash YAML и base64-список `vless://`.
3. Либо с компьютера, без набора на часах:

```sh
adb shell am start -a android.intent.action.VIEW \
  -d 'watchvpn://import?url=https://example.test/subscribe'
```

Подставьте **свою** HTTPS-ссылку (URL лучше URL-encoded). Схему `clash://install-config?url=` тоже принимает.

4. Дождитесь списка **Серверы**. Нажмите на узел — появится **✓**.
5. **Подключить**. Статус: «Прокси 127.0.0.1:7890».
6. Если grant не выдан — сразу команда `settings put global http_proxy 127.0.0.1:7890`.
7. **Пинг** на строке сервера — замер через mihomo (`generate_204`).

Ссылка хранится зашифрованной (Android Keystore). В логах виден только хост.

Панель может резать подписку по User-Agent: по умолчанию `v2raytun/android`, при заглушках перебираются v2rayNG / Happ / Clash / Shadowrocket.

## 6. Что проксируется

1. Часы в Wi‑Fi или LTE, Watch VPN **Подключено**, `http_proxy=127.0.0.1:7890`.
2. Телефон **не** нужен.
3. Браузер и приложения, которые читают системный HTTP-прокси, идут через выбранный VLESS-узел.
4. **WhatsApp / Telegram / многие мессенджеры — нет.** Им нужен TUN, а его на этих часах нет.
5. **Отключить** (или смерть движка) снимает `http_proxy`, интернет напрямую возвращается.

Плитка: тайл Watch VPN на циферблате или быстрые настройки. Нажатие переключает прокси.

## Если не подключается

| Симптом | Что делать |
|---|---|
| Нет интернета у всех приложений | `settings put global http_proxy :0` — снять мёртвый прокси |
| Connect ок, браузер всё ещё напрямую | Нет grant — выполните `settings put global http_proxy 127.0.0.1:7890` |
| `NullPointerException` / IVpnManager | Это ожидаемо. TUN выключен, нужен прокси-режим 0.2.0 |
| WhatsApp «нет сети» или без прокси | Мессенджер игнорирует `http_proxy`. Так и должно быть |
| Движок сразу падает | Нет `libmihomo.so`, либо OEM запретил `exec`. См. **Журнал** |
| Подписка — заглушки «не поддерживается» | **Справка / UA** → v2RayTun → **Обновить подписку** |

## Подпись APK

Sideload-ключ (не Play Store), тот же JKS, что у Grok Wear этой среды:

- Файл: `grok-wear-release.jks` (артефакты агента, **не** в git)
- Alias: `grokwear`
- DN: `CN=Grok Wear sideload, O=aquanetworks, C=US`

Обновления поверх этой установки требуют тот же ключ. Другой `applicationId`, поэтому Watch VPN и Grok Wear ставятся рядом.

## Что не проверялось на живых часах

См. раздел в [README.md](README.md): реальный `http_proxy` на Galaxy Watch Ultra после 0.2.0, браузер через узел, grant `WRITE_SECURE_SETTINGS`, watchdog при падении mihomo.
