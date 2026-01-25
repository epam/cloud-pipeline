# pipe-cli Timeout Parameters Reference

Полное описание параметров таймаутов, используемых в проекте `pipe-cli`.

## 1. API Таймауты

**Расположение:** `src/api/base.py`

- **`CP_CLI_API_CALL_RETRY_TIMEOUT`** (переменная окружения)
  - **По умолчанию:** `5` секунд
  - **Описание:** Таймаут для повторных попыток API-запросов
  - **Код:** `self.__timeout__ = int(os.getenv('CP_CLI_API_CALL_RETRY_TIMEOUT') or 5)`

- **`CP_CLI_API_CALL_RETRY_ATTEMPTS`** (переменная окружения)
  - **По умолчанию:** `3` попытки
  - **Описание:** Количество повторных попыток API-запросов
  - **Код:** `self.__attempts__ = int(os.getenv('CP_CLI_API_CALL_RETRY_ATTEMPTS') or 3)`

- **`__connection_timeout__`** (внутренний параметр)
  - **По умолчанию:** `10` секунд
  - **Описание:** Таймаут подключения к API
  - **Код:** `self.__connection_timeout__ = 10`

## 2. Storage Transfer Таймауты

**Расположение:** `src/utilities/datastorage_operations.py`

- **`CP_CLI_TRANSFER_RETRY_TIMEOUT`** (переменная окружения)
  - **По умолчанию:** `15` секунд
  - **Константа:** `TRANSFER_RETRY_TIMEOUT`
  - **Описание:** Таймаут между повторными попытками передачи файлов
  - **Код:** `TRANSFER_RETRY_TIMEOUT = int(os.getenv('CP_CLI_TRANSFER_RETRY_TIMEOUT', 15))`

- **`CP_CLI_TRANSFER_RETRY_ATTEMPTS`** (переменная окружения)
  - **По умолчанию:** `3` попытки
  - **Константа:** `TRANSFER_RETRY_ATTEMPTS`
  - **Описание:** Количество повторных попыток передачи файлов
  - **Код:** `TRANSFER_RETRY_ATTEMPTS = int(os.getenv('CP_CLI_TRANSFER_RETRY_ATTEMPTS', 3))`

## 3. Tunnel Command Таймауты

**Расположение:** `pipe.py` (команда `tunnel start`)

- **`--timeout`**
  - **По умолчанию:** `300` секунд
  - **Тип:** CLI аргумент
  - **Описание:** Таймаут для туннеля в секундах; 0 = без ограничений
  - **Флаг:** `@click.option('--timeout', type=int, default=300, ...)`

- **`--timeout-stop`**
  - **По умолчанию:** `60` секунд
  - **Тип:** CLI аргумент
  - **Описание:** Таймаут для остановки туннеля при превышении основного таймаута
  - **Флаг:** `@click.option('--timeout-stop', type=int, default=60, ...)`

- **`--connection-timeout`**
  - **По умолчанию:** `0` (без ограничений)
  - **Тип:** CLI аргумент
  - **Описание:** Таймаут установления соединения туннеля в секундах
  - **Флаг:** `@click.option('--connection-timeout', type=int, default=0, ...)`

## 4. Run Command Таймауты

**Расположение:** `pipe.py` (команда `run`)

- **`--timeout`**
  - **По умолчанию:** не установлен (опционально)
  - **Тип:** CLI аргумент
  - **Единицы измерения:** минуты
  - **Описание:** Таймаут выполнения задачи
  - **Флаг:** `@click.option('--timeout', type=int, ...)`

## 5. Storage Mount Таймауты

**Расположение:** `pipe.py` (команда `storage mount`)

- **`--timeout`**
  - **По умолчанию:** `10000` миллисекунд
  - **Тип:** CLI аргумент
  - **Описание:** Таймаут операций монтирования хранилища
  - **Флаг:** `@click.option('--timeout', type=int, default=10000, ...)`

## 6. AWS API Таймауты

**Расположение:** `pipe.py` (документация переменных окружения)

- **`CP_AWS_MAX_ATTEMPTS`** (переменная окружения)
  - **По умолчанию:** не установлен (опционально)
  - **Описание:** Максимальное количество попыток для AWS API запросов
  - **Примечание:** Используется AWS SDK

## Общие рекомендации

1. **Переменные окружения** имеют приоритет над дефолтными значениями
2. **API таймауты** применяются ко всем HTTP-запросам к Cloud Pipeline API
3. **Transfer таймауты** специфичны для операций с хранилищем (cp/mv/sync)
4. **Tunnel таймауты** контролируют жизненный цикл SSH-туннелей
5. **Значение 0** обычно означает "без ограничений" для таймаутов

## Связанные файлы

- Основная логика API: `src/api/base.py`
- Операции с хранилищем: `src/utilities/datastorage_operations.py`
- CLI команды: `pipe.py`
- SSH туннели: `src/utilities/ssh_operations.py`