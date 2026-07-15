# Backend-прокси AI

Минимальный прокси между Android-приложением и AI-провайдером.
API-ключ провайдера хранится ТОЛЬКО в переменных окружения сервера
и никогда не попадает в APK.

## Запуск локально

```bash
cp .env.example .env       # заполните ANTHROPIC_API_KEY
pip install -r requirements.txt
export $(grep -v '^#' .env | xargs)
uvicorn main:app --host 0.0.0.0 --port 8080
```

## Docker

```bash
docker build -t scannerai-proxy .
docker run -p 8080:8080 -e ANTHROPIC_API_KEY=... scannerai-proxy
```

## Endpoints

- `POST /v1/summarize` — `{"text": "...", "language": "ru"}` → структура AiSummary
- `POST /v1/extract` — извлечение реквизитов (ExtractedDocumentData)
- `POST /v1/contract` — анализ договора (ContractAnalysis)
- `GET /health`

Защита: лимит размера текста (413), rate limiting на IP (429), таймауты
к провайдеру (504), серверная валидация (422 от pydantic). Тексты
документов не пишутся в логи — логируются только тип запроса и длина.

## Подключение Android-приложения

```bash
./gradlew :composeApp:assembleRelease -PaiBaseUrl=https://your-proxy.example.com
# или переменной окружения AI_BASE_URL
```
