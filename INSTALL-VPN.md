# Установка Watch VPN (системный HTTP-прокси на часах)

Отдельное приложение **Watch VPN** (`com.aquanetworks.watchvpn`) поднимает **mihomo in-process** (`libmihomo-jni.so` в том же процессе, что и foreground-сервис). Mixed HTTP/SOCKS слушает **`127.0.0.1:7890`**. Системный `http_proxy` направляет туда приложения, которые его уважают.

Это **не TUN / не VpnService**. На Galaxy Watch Ultra нет `IVpnManager`. Exec-потомок `libmihomo.so` Android убивает (phantom process) — в 0.3.0 его нет.

**WhatsApp, Telegram и многие мессенджеры игнорируют системный HTTP-прокси.**

**APK:** `watch-vpn-armeabi-v7a.apk` (версия **0.3.0**). Подпись — тот же sideload-ключ, что и Grok Wear.

> Боевую ссылку подписки в репозиторий и в чаты не кладите.

## 1. Режим разработчика на часах

1. **Настройки → О часах → Сведения о ПО**.
2. Нажмите **Номер сборки** семь раз.
3. **Параметры разработчика**: **Отладка по ADB**, **беспроводная отладка**.

## 2. Установка

WearLoad / `adb install -r --no-streaming watch-vpn-armeabi-v7a.apk` (тот же ключ, что 0.1–0.2).

## 3. Команды aShell You (без префикса `adb shell`), по порядку

**Обязательно один раз:**

```
pm grant com.aquanetworks.watchvpn android.permission.WRITE_SECURE_SETTINGS
```

Потом откройте Watch VPN → **Подключить**. Зелёный «Прокси 127.0.0.1:7890» появляется **только** если порт 7890 принимает TCP **и** проверка узла (generate_204) прошла. Тогда приложение само ставит `http_proxy`.

Пока grant нет — после зелёного Connected вручную:

```
settings put global http_proxy 127.0.0.1:7890
```

Если нет интернета (прокси мёртв) — **сразу**:

```
settings put global http_proxy :0
```

Эта строка красным на экране, если grant нет и проверка не прошла.

Опционально (Doze / батарея; на Wear нет «игнорировать оптимизацию»):

```
dumpsys deviceidle whitelist +com.aquanetworks.watchvpn
```

## 4. Подписка

**Подписка** в приложении или:

```
am start -a android.intent.action.VIEW -d 'watchvpn://import?url=https://example.test/subscribe'
```

Нажмите сервер (✓) → **Подключить**. **Пинг** меряет задержку через API mihomo.

## 5. Что проксируется

Браузер и приложения, которые читают `http_proxy`. **WhatsApp / Telegram — нет.**

Отключить / падение движка / неудачная проверка раз в ~10 с снимает `http_proxy` (если grant есть).

## Если не подключается

| Симптом | Что делать |
|---|---|
| Нет интернета у всех приложений | `settings put global http_proxy :0` |
| Connected без реального прокси (0.2.0) | Поставьте 0.3.0: in-process, Connected только после TCP+health |
| WhatsApp без прокси | Ожидаемо |
| Подписка-заглушки | Справка / UA → v2RayTun → Обновить |

## Подпись APK

Sideload-ключ: alias `grokwear`, DN `CN=Grok Wear sideload, O=aquanetworks, C=US`.
