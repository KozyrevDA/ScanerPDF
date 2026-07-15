"""Минимальный backend-прокси AI для «Сканер документов AI: PDF».

Безопасность:
- API-ключ провайдера только в переменных окружения (ANTHROPIC_API_KEY);
- тексты документов не пишутся в логи;
- лимит размера входного текста;
- таймауты и rate limiting.
"""
import asyncio
import json
import logging
import os
import time
from collections import defaultdict, deque

import httpx
from fastapi import FastAPI, HTTPException, Request
from pydantic import BaseModel, Field

MAX_TEXT_CHARS = int(os.getenv("MAX_TEXT_CHARS", "60000"))
UPSTREAM_TIMEOUT_S = float(os.getenv("UPSTREAM_TIMEOUT_S", "45"))
RATE_LIMIT_PER_MINUTE = int(os.getenv("RATE_LIMIT_PER_MINUTE", "20"))
ANTHROPIC_API_KEY = os.getenv("ANTHROPIC_API_KEY", "")
ANTHROPIC_MODEL = os.getenv("ANTHROPIC_MODEL", "claude-sonnet-4-6")

logging.basicConfig(level=logging.INFO)
log = logging.getLogger("ai-proxy")

app = FastAPI(title="ScannerAI proxy", docs_url=None, redoc_url=None)

_requests: dict[str, deque] = defaultdict(deque)


def check_rate_limit(client_ip: str) -> None:
    now = time.monotonic()
    window = _requests[client_ip]
    while window and now - window[0] > 60:
        window.popleft()
    if len(window) >= RATE_LIMIT_PER_MINUTE:
        raise HTTPException(status_code=429, detail="rate limit exceeded")
    window.append(now)


class TextRequest(BaseModel):
    text: str = Field(min_length=1)
    language: str = Field(default="ru", max_length=8)


SUMMARIZE_SCHEMA = (
    '{"documentType": str|null, "shortSummary": str, "keyPoints": [str], '
    '"dates": [{"value": str, "description": str|null}], '
    '"amounts": [{"value": str, "currency": str|null, "description": str|null}], '
    '"requiredActions": [str]}'
)
EXTRACT_SCHEMA = (
    '{"fields": [{"name": str, "value": str, "page": int|null, '
    '"sourceFragment": str|null, "confidence": float|null}]}'
)
CONTRACT_SCHEMA = (
    '{"importantTerms": [{"title": str, "description": str, "sourceFragment": str|null}], '
    '"risks": [...], "deadlines": [...], "moneyTerms": [...], "questionsToClarify": [str]}'
)

PROMPTS = {
    "summarize": (
        "Ты анализируешь распознанный текст документа. Верни ТОЛЬКО JSON по схеме "
        f"{SUMMARIZE_SCHEMA}. Не выдумывай данные: включай только то, что есть в тексте. "
        "Язык ответа: {language}."
    ),
    "extract": (
        "Извлеки реквизиты из текста документа (ФИО, организации, ИНН, КПП, ОГРН, номера "
        "договоров, даты, суммы, адреса, телефоны, email, банковские реквизиты, счета, БИК, "
        "сроки оплаты, номер документа). Верни ТОЛЬКО JSON по схеме "
        f"{EXTRACT_SCHEMA}. Не выдумывай отсутствующие данные. Язык ответа: {{language}}."
    ),
    "contract": (
        "Проанализируй договор: штрафы, пени, автопродление, обязательные платежи, скрытые "
        "комиссии, одностороннее изменение условий, ограничения расторжения, сроки уведомления, "
        "передача персональных данных и прав третьим лицам, подсудность, отказ от гарантий, "
        "материальная ответственность, спорные формулировки. Верни ТОЛЬКО JSON по схеме "
        f"{CONTRACT_SCHEMA}. Не утверждай, что договор безопасен. Язык ответа: {{language}}."
    ),
}


async def call_model(kind: str, payload: TextRequest) -> dict:
    if not ANTHROPIC_API_KEY:
        raise HTTPException(status_code=503, detail="provider key is not configured")
    if len(payload.text) > MAX_TEXT_CHARS:
        raise HTTPException(status_code=413, detail="text too large")

    system = PROMPTS[kind].replace("{language}", payload.language)
    body = {
        "model": ANTHROPIC_MODEL,
        "max_tokens": 2048,
        "system": system,
        "messages": [{"role": "user", "content": payload.text}],
    }
    async with httpx.AsyncClient(timeout=UPSTREAM_TIMEOUT_S) as client:
        try:
            response = await client.post(
                "https://api.anthropic.com/v1/messages",
                headers={
                    "x-api-key": ANTHROPIC_API_KEY,
                    "anthropic-version": "2023-06-01",
                    "content-type": "application/json",
                },
                json=body,
            )
        except httpx.TimeoutException as exc:
            raise HTTPException(status_code=504, detail="upstream timeout") from exc
    if response.status_code != 200:
        # Текст документа и тело ответа провайдера в логи не пишем
        log.warning("upstream error kind=%s status=%s", kind, response.status_code)
        raise HTTPException(status_code=502, detail="upstream error")

    raw = "".join(
        block.get("text", "")
        for block in response.json().get("content", [])
        if block.get("type") == "text"
    ).strip()
    if raw.startswith("```"):
        raw = raw.strip("`")
        raw = raw[raw.find("{"):]
    try:
        return json.loads(raw)
    except json.JSONDecodeError as exc:
        log.warning("invalid model json kind=%s", kind)
        raise HTTPException(status_code=502, detail="invalid upstream response") from exc


def endpoint(kind: str):
    async def handler(request: Request, payload: TextRequest) -> dict:
        check_rate_limit(request.client.host if request.client else "unknown")
        log.info("request kind=%s chars=%d", kind, len(payload.text))
        return await call_model(kind, payload)

    return handler


app.post("/v1/summarize")(endpoint("summarize"))
app.post("/v1/extract")(endpoint("extract"))
app.post("/v1/contract")(endpoint("contract"))


@app.get("/health")
async def health() -> dict:
    return {"status": "ok", "provider_configured": bool(ANTHROPIC_API_KEY)}
