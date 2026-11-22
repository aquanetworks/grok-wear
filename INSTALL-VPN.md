# Установка Watch VPN (системный VPN на часах)

Отдельное приложение **Watch VPN** (`com.aquanetworks.watchvpn`) поднимает **системный TUN** через Android `VpnService`. Весь трафик часов (WhatsApp и остальные приложения) идёт через подписку VLESS + Reality. Корня, Shizuku и глобального `http_proxy` нет.

Это **не** Grok Wear. Grok Wear (PR с in-app прокси) остаётся отдельным APK и не маршрутизирует чужие приложения.

**APK:** `watch-vpn-armeabi-v7a.apk` (~76 МБ из‑за mihomo `armeabi-v7a`, `compileSdk`/`targetSdk` 36). Подпись — тот же sideload-ключ, что и Grok Wear, см. [Подпись](#подпись-apk).

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
5. Если стоит старая сборка с **другой** подписью: `adb uninstall com.aquanetworks.watchvpn` и снова install.

## 3. Установка с компьютера (ADB Wi‑Fi)

```sh
adb pair <IP>:<порт-сопряжения>
adb connect <IP>:<рабочий-порт>
adb install -r --no-streaming watch-vpn-armeabi-v7a.apk
```

`--no-streaming` надёжнее на часовом Wi‑Fi.

## 4. Разрешение VPN (самое важное на Wear)

На круглых Galaxy Watch диалог `VpnService.prepare()` часто **отсутствует** или сразу закрывается с `RESULT_CANCELED`. Без разрешения TUN не поднимется.

Проверка в приложении: **Справка / appops** — строки `VpnService.prepare` и `appops ACTIVATE_VPN`.

Если согласия нет, с компьютера (без root, через тот же WearLoad/ADB):

```sh
adb shell appops set com.aquanetworks.watchvpn ACTIVATE_VPN allow
```

После команды откройте Watch VPN и нажмите **Подключить**. Если `prepare` всё ещё «нужен диалог», перезапустите приложение.

Всегда включённый VPN (по желанию): **Настройки часов → Подключения → VPN** (путь может отличаться на One UI Watch) → Watch VPN → **Always-on VPN**. Блокировка трафика без VPN — там же, если пункт есть.

## 5. Подписка и сервер

1. Откройте **Watch VPN**.
2. **Подписка** — введите HTTPS-ссылку v2RayTun (клавиатура / диктовка Wear). Подойдут Clash YAML и base64-список `vless://`.
3. Либо с компьютера, без набора на часах:

```sh
adb shell am start -a android.intent.action.VIEW \
  -d 'clash://install-config?url=https://example.test/subscribe'
```

Подставьте **свою** HTTPS-ссылку (URL лучше URL-encoded). Схему `watchvpn://import?url=` тоже принимает.

4. Дождитесь списка **Серверы**. Выберите узел.
5. **Подключить**. Статус должен стать зелёным «Подключено».
6. **Пинг** на строке сервера — замер через выбранный узел (`generate_204`). Без реального сервера замер не пройдёт.

Ссылка хранится зашифрованной (Android Keystore). В логах и в статусе виден только хост, без `token=`.

## 6. Проверка, что VPN системный

1. Часы в Wi‑Fi или LTE, Watch VPN **Подключено**.
2. Телефон **не** нужен.
3. Откройте **WhatsApp** (или браузер) на часах. Трафик должен идти через туннель.
4. Если WhatsApp «нет сети» при зелёном VPN: другой сервер, **Обновить подписку**, либо смотрите ошибку на главном экране.
5. **Отключить** — приложения снова выходят напрямую.

Плитка: добавьте тайл Watch VPN на циферблат (долгое касание циферблата → тайлы) или плитку быстрых настроек, если One UI Watch её показывает. Нажатие переключает туннель.

## Если не подключается

| Симптом | Что делать |
|---|---|
| «нет VPN consent screen» / prepare не готов | `adb shell appops set com.aquanetworks.watchvpn ACTIVATE_VPN allow` |
| Движок сразу падает | Нет `libmihomo.so`, либо OEM запретил `exec` из `nativeLibraryDir`. См. README, «не проверено». |
| Подписка не читается | Нужны Clash YAML, base64 URI или `vless://`. HTTP (не HTTPS) ссылки отвергаются. |
| Петля / нет интернета у всех приложений | Движок должен быть исключён из TUN (`addDisallowedApplication`). Если OEM это игнорирует — отключите VPN и сообщите модель часов. |
| IPv6 ошибка при establish | Сборка пробует IPv4+IPv6 и откатывается на IPv4. |

## Подпись APK

Sideload-ключ (не Play Store), тот же JKS, что у Grok Wear этой среды:

- Файл: `grok-wear-release.jks` (артефакты агента, **не** в git)
- Alias: `grokwear`
- DN: `CN=Grok Wear sideload, O=aquanetworks, C=US`

Обновления поверх этой установки требуют тот же ключ. Другой `applicationId`, поэтому Watch VPN и Grok Wear ставятся рядом.

## Что не проверялось на живых часах

См. раздел в [README.md](README.md): реальное рукопожатие VLESS/Reality, WhatsApp через TUN, диалог согласия Wear, LTE, батарея, always-on.
