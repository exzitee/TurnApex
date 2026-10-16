# ▲ TurnApex • Stealth Anti-Censorship System (OKX Design System)

> **Высокопроизводительная система обхода белых списков и блокировок РКН через инфраструктуру TURN-серверов VK Звонков.**
> Написана с нуля на **Rust** (асинхронное ядро `tokio`) с Android-клиентом на **Kotlin (Jetpack Compose) + Rust FFI / JNI** в стилистике **OKX Design System**.

---

## 📋 Оглавление
1. [Дизайн-система OKX](#-дизайн-система-okx)
2. [Архитектура проекта](#-архитектура-проекта)
3. [Ключевые особенности](#-ключевые-особенности)
   - [3.1 Multi-TURN пул (Failover & Aggregate)](#31-multi-turn-пул-failover--aggregate)
   - [3.2 RTP-маскировка трафика (под видеозвонки VK)](#32-rtp-маскировка-трафика-под-видеозвонки-vk)
   - [3.3 Health Checker и мгновенный Failover (<50ms)](#33-health-checker-и-мгновенный-failover-50ms)
   - [3.4 Раздельное туннелирование (Split Tunneling)](#34-раздельное-туннелирование-split-tunneling)
   - [3.5 Режим «Для родителей»](#35-режим-для-родителей)
4. [Автодеплой VPS (в 1 клик)](#-автодеплой-vps-в-1-клик)
5. [Сборка и установка](#-сборка-и-установка)
   - [Сервер (`bypassd`)](#сервер-bypassd)
   - [Android-клиент (`TurnApex`)](#android-клиент-turnapex)
   - [CI/CD (GitHub Actions)](#cicd-github-actions)
6. [Конфигурация (JSON)](#-конфигурация-json)

---

## 🖤 Дизайн-система OKX

Приложение полностью переработано по канонам **OKX Design System**:
- **Цветовая палитра**: Глубокий черный AMOLED фон (`#000000`), премиальные темные поверхности контейнеров (`#0E0E10`, `#17171A`), тонкие разделительные границы (`#27272A`).
- **Акценты**: Фирменный неоново-зеленый цвет OKX (`#00D665`) для активных соединений, сигнальный красный (`#F6465D`) для ошибок и предупреждений, контрастный чистый белый (`#FFFFFF`).
- **Стилистика компонентов**:
  - Скругления карточек $14\text{--}16\text{ dp}$ с 1-пиксельной границей.
  - Сегментированные переключатели (Segmented Controls) для выбора режима Multi-TURN (`[ AGGREGATE ]` vs `[ FAILOVER ]`).
  - Индикаторы метрик в стиле крипто-тикеров: крупный моноширинный шрифт скоростей (`↓ 8.2 Mbps`, `↑ 3.1 Mbps`) и сетевой задержки (`34 ms`).
  - Карточки целевых сервисов (Telegram, Google, YouTube, TikTok) со статусом `LIVE` и пульсирующими зелеными точками.
  - Список раздельного туннелирования в стиле портфолио активов (Asset List) с контрастными бейджами `ТУННЕЛЬ` / `НАПРЯМУЮ`.
  - Режим «Для родителей» с крупным щитом безопасности OKX Apex Shield и кнопкой `[ ИНТЕРНЕТ ВКЛЮЧЕН ]`.

---

## 🏛 Архитектура проекта

```text
.
├── bypass-core/           # Общая Rust-библиотека (ядро системы)
│   ├── src/
│   │   ├── config.rs      # Конфигурация, профили, режимы работы
│   │   ├── turn/          # Реализация RFC 5766 TURN & RFC 5389 STUN
│   │   │   ├── stun.rs    # Кодирование/декодирование STUN сообщений и ChannelData
│   │   │   ├── client.rs  # Одиночный TURN-клиент (Allocate, CreatePerm, Refresh)
│   │   │   └── pool.rs    # Multi-TURN пул (Failover + Aggregate, DNS-резолвер)
│   │   ├── rtp/           # Маскировка трафика под видеозвонки WebRTC
│   │   │   └── mask.rs    # RTP-заголовок, таймстемпы 30fps, ротация SSRC
│   │   ├── health/        # Мониторинг качества связи и проверка сервисов
│   │   │   └── checker.rs # STUN ping + TCP probe (Telegram, Google, YouTube)
│   │   ├── tunnel/        # Сетевые интерфейсы и маршрутизация
│   │   │   ├── split.rs   # Диспетчер раздельного туннелирования (ALL/LIST/EXCEPT)
│   │   │   ├── tun.rs     # Обработчик TUN интерфейса (MTU 1280)
│   │   │   └── socks5.rs  # Локальный SOCKS5-прокси (порт 9000)
│   │   ├── crypto/        # Защита трафика между клиентом и VPS
│   │   │   └── dtls.rs    # DTLS 1.2 с 0-RTT session resumption
│   │   ├── stats/         # Метрики скорости (Mbps), пинга и пакетов
│   │   └── ffi/           # JNI мост для Android VpnService
├── bypassd/               # Серверный демон для VPS
│   └── src/
│       ├── main.rs        # CLI интерфейс (порт 56000)
│       ├── server.rs      # Демультиплексор RTP/DTLS и ретранслятор в WireGuard
│       └── deploy.rs      # Модуль автонастройки (--auto-deploy)
├── bypass-app/            # Android-клиент TurnApex (Kotlin + Jetpack Compose)
│   └── app/
│       └── src/main/
│           ├── java/.../  # OKX UI, VpnService, JNI Native Wrapper
│           └── AndroidManifest.xml
├── .github/workflows/     # CI/CD автоматическая сборка APK и бинарников Linux
└── deploy.sh              # Скрипт быстрого развертывания сервера
```

---

## 🚀 Ключевые особенности

### 3.1 Multi-TURN пул (Failover & Aggregate)
Пул управляет соединениями со встроенными TURN-серверами VK (`turn.vk.com:3478`, `turn-ttl.vk.com:3478`, `5.255.211.241:3478`), а также динамически резолвит все `A`-записи домена `turn.vk.com`.

- **Режим `FAILOVER`:**
  - Приложение сразу устанавливает аллокации со всеми TURN-серверами VK.
  - Один сервер выбирается в качестве активного (primary) по наименьшему пингу.
  - Остальные серверы поддерживаются в состоянии **«горячего резерва»** (периодический keep-alive / refresh).
  - При сбое или отсутствии ответа за 3 сек происходит мгновенное переключение на резервный канал за **< 50 мс** без разрыва текущих TCP-соединений.
- **Режим `AGGREGATE`:**
  - Все доступные TURN-серверы активны одновременно.
  - Исходящие и входящие пакеты распределяются round-robin.
  - Суммарная пропускная способность канала возрастает кратно числу серверов ($N \times 3\text{--}5\text{ Mbps}$).

### 3.2 RTP-маскировка трафика (под видеозвонки VK)
Трафик туннеля инкапсулируется в аутентичные RTP-пакеты (RFC 3550):
```rust
struct RtpHeader {
    version: u8,          // 2 (RTP)
    payload_type: u8,     // 96..127 (динамический тип видео VP8/H.264)
    sequence_number: u16, // строго монотонный счетчик
    timestamp: u32,       // инкремент на ~3000 (эмуляция 30 fps видео)
    ssrc: u32,            // идентификатор источника с регулярной ротацией
    payload: Vec<u8>,     // зашифрованный полезный трафик
}
```
**Результат для ТСПУ / DPI:** трафик идентифицируется как обычный WebRTC видеозвонок внутри экосистемы VK, на который не действуют белые списки и ограничения.

### 3.3 Health Checker и мгновенный Failover (<50ms)
- Каждые 3 секунды отправляется `STUN Binding Request` для измерения задержки RTT.
- Регулярные TCP-проверки доступности целевых ресурсов:
  - `telegram.org:443`
  - `google.com:443`
  - `youtube.com:443`
  - `tiktok.com:443`
- Если все TURN-серверы недоступны, включается экспоненциальный backoff на 10 секунд с последующим повторным DNS-поиском.

### 3.4 Раздельное туннелирование (Split Tunneling)
Поддерживаются 3 режима:
1. **`ALL`** — весь сетевой стек смартфона направляется в туннель (режим по умолчанию для родителей).
2. **`LIST`** — через туннель идут только выбранные приложения (`Telegram`, `YouTube`, `TikTok`).
3. **`EXCEPT`** — всё идет через туннель, кроме критичных российских сервисов (банки, Госуслуги, Госуслуги Авто).

Реализация выполнена через официальный API Android `VpnService.Builder.addAllowedApplication()` и `addDisallowedApplication()` без необходимости root-прав.

### 3.5 Режим «Для родителей»
В настройках включается специальный одноэкранный интерфейс:
- Крупная индикация: **TURNAPEX ЗАЩИТА**
- Большая зеленая кнопка: **[ ИНТЕРНЕТ ВКЛЮЧЕН ]**
- Лаконичная инструкция: **«Ничего не трогай. Всё работает автоматически.»**

---

## 🖥 Автодеплой VPS (в 1 клик)

### Вариант 1: Через встроенный мастер в Android-приложении
1. Откройте **TurnApex** -> **Настройки** -> **Развернуть VPS**.
2. Введите IP адрес, порт SSH (22), пользователя (`root`) и пароль / SSH-ключ.
3. Нажмите **«ЗАПУСТИТЬ ДЕПЛОЙ»**. Приложение подключится по SSH, выполнит установку WireGuard, сгенерирует ключи, настроит iptables NAT, фаервол UFW и запустит демон `bypassd`.

### Вариант 2: Запуск одной командой на сервере
На чистом сервере Ubuntu / Debian выполните:
```bash
curl -sSL https://raw.githubusercontent.com/your-repo/turnapex/main/deploy.sh | sudo bash
```
Скрипт автоматически выведет готовый JSON-блок для вставки в приложение.

---

## ⚙️ Сборка и установка

### Сервер (`bypassd`)
Требуется установленный Rust 1.75+:
```bash
cargo build --release -p bypassd
# Бинарник: target/release/bypassd
./target/release/bypassd --listen 0.0.0.0:56000 --wireguard-target 127.0.0.1:51820
```

### Android-клиент (`TurnApex`)
Сборка кроссплатформенной библиотеки `.so` и APK:
```bash
# 1. Установите целевую архитектуру Android
rustup target add aarch64-linux-android
cargo install cargo-ndk

# 2. Скомпилируйте libbypass_core.so
cd bypass-core
cargo ndk -t arm64-v8a -o ../bypass-app/app/src/main/jniLibs build --release

# 3. Соберите APK
cd ../bypass-app
./gradlew assembleDebug
# APK готов: bypass-app/app/build/outputs/apk/debug/TurnApex-debug.apk
```

### CI/CD (GitHub Actions)
В репозиторий включен пайплайн `.github/workflows/build.yml`. При каждом коммите в ветку `main`:
- Автоматически компилируется бинарный файл `bypassd-linux-x86_64`.
- Компилируется Android APK `TurnApex-debug.apk` со встроенной нативной библиотекой под архитектуру `arm64-v8a`.

---

## 📄 Конфигурация (`config.json`)

```json
{
  "vps": {
    "ip": "1.2.3.4",
    "port": 56000,
    "psk": "vk-turn-bypass-secret-v1"
  },
  "turn_servers": [
    "turn.vk.com:3478",
    "turn-ttl.vk.com:3478",
    "5.255.211.241:3478"
  ],
  "mode": "aggregate",
  "rtp_masking": true,
  "split_tunnel": {
    "mode": "list",
    "tunnel_apps": [
      "org.telegram.messenger",
      "com.google.android.youtube",
      "com.zhiliaoapp.musically"
    ],
    "direct_domains": [
      "sberbank.ru",
      "gosuslugi.ru",
      "tinkoff.ru"
    ]
  },
  "health_interval_sec": 3,
  "failover_timeout_sec": 3,
  "mtu": 1280,
  "n_streams": 4,
  "profiles": ["home", "work", "parents"]
}
```
