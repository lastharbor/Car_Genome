# CarGenome Cloud Sync Server

Легковесный сервер синхронизации данных CarGenome между мобильными устройствами (Android).

## Возможности
- 🔐 Регистрация и авторизация пользователей по Email / Password (JWT токены).
- 🔄 Синхронизация истории автомобилей, заправок, сервисных записей, регламентов ТО, календаря и расходов.
- 📱 Поддержка нескольких телефонов на один аккаунт.
- 💾 SQLite база данных с автоматическим созданием таблиц (нулевая настройка).
- 🐳 Готовый Dockerfile и `docker-compose.yml` для развертывания на любом VPS или домашнем сервере (Synology, Raspberry Pi).

## Быстрый запуск

Сервер не запустится без `SECRET_KEY`: этим ключом подписываются токены, и тот, кто его знает,
может войти под любым пользователем. Ключ генерируется один раз и хранится только на сервере:

```bash
cd server
echo "SECRET_KEY=$(python -c 'import secrets; print(secrets.token_urlsafe(48))')" > .env
```

Файл `.env` в git не попадает. Если ключ поменять, все пользователи должны будут войти заново.

### Вариант 1: Через Docker (рекомендуется)
```bash
cd server
docker compose up -d
```
Сервер запустится на `http://localhost:8000`.

### Вариант 2: Локально через Python
```bash
cd server
pip install -r requirements.txt
export $(cat .env)
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

## API Эндпоинты
Документация Swagger UI доступна по адресу: `http://localhost:8000/docs`

- `POST /api/v1/auth/register` — Регистрация нового аккаунта
- `POST /api/v1/auth/login` — Вход и получение JWT токена
- `GET /api/v1/sync/status` — Проверка статуса синхронизации и ревизии
- `POST /api/v1/sync/push` — Загрузка локальных данных на сервер
- `GET /api/v1/sync/pull` — Скачивание актуальных данных с сервера
- `GET /health` — Health check
