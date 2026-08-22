import base64
import json
import math
import os
from pathlib import Path
from typing import List

import httpx
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response, StreamingResponse
from fastapi.staticfiles import StaticFiles
from openai import OpenAI
from pydantic import BaseModel

load_dotenv(Path(__file__).resolve().parent / ".env")

API_KEY = os.getenv("NVIDIA_API_KEY")
BASE_URL = os.getenv("NVIDIA_BASE_URL", "https://integrate.api.nvidia.com/v1")
MODEL = os.getenv("GLM_MODEL", "meta/llama-3.3-70b-instruct")
VISION_MODEL = os.getenv("GLM_VISION_MODEL", "meta/llama-3.2-90b-vision-instruct")
SYSTEM_PROMPT = "Você é um assistente útil, direto e honesto. Responda em português, a menos que o usuário escreva em outro idioma."

GOOGLE_MAPS_API_KEY = os.getenv("GOOGLE_MAPS_API_KEY")

if not API_KEY:
    raise RuntimeError(
        "NVIDIA_API_KEY não configurada. Copie .env.example para .env "
        "(local) ou defina a variável de ambiente NVIDIA_API_KEY (produção/Vercel)."
    )

client = OpenAI(base_url=BASE_URL, api_key=API_KEY)

app = FastAPI(title="GLM 5.2 Chat")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


class Message(BaseModel):
    role: str
    content: str


class ChatRequest(BaseModel):
    messages: List[Message]


@app.post("/api/chat")
def chat(req: ChatRequest):
    if not req.messages:
        raise HTTPException(status_code=400, detail="Nenhuma mensagem enviada.")

    payload = [{"role": "system", "content": SYSTEM_PROMPT}] + [
        {"role": m.role, "content": m.content} for m in req.messages
    ]

    def generate():
        try:
            stream = client.chat.completions.create(
                model=MODEL,
                messages=payload,
                temperature=1,
                top_p=1,
                max_tokens=16384,
                stream=True,
            )
            for chunk in stream:
                if not getattr(chunk, "choices", None):
                    continue
                delta = chunk.choices[0].delta
                content = getattr(delta, "content", None)
                if content:
                    yield content
        except Exception as exc:  # repassa o erro para o chat em vez de quebrar a resposta
            yield f"\n\n[Erro ao consultar o modelo: {exc}]"

    return StreamingResponse(generate(), media_type="text/plain; charset=utf-8")


def _bearing_deg(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    """Ângulo (heading) de (lat1,lng1) até (lat2,lng2), em graus [0, 360)."""
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    d_lambda = math.radians(lng2 - lng1)
    y = math.sin(d_lambda) * math.cos(phi2)
    x = math.cos(phi1) * math.sin(phi2) - math.sin(phi1) * math.cos(phi2) * math.cos(d_lambda)
    return (math.degrees(math.atan2(y, x)) + 360) % 360


async def _resolve_storefront_view(address: str) -> dict:
    if not GOOGLE_MAPS_API_KEY:
        raise HTTPException(
            status_code=500,
            detail="GOOGLE_MAPS_API_KEY não configurada no servidor.",
        )

    async with httpx.AsyncClient(timeout=15) as http:
        geocode_resp = await http.get(
            "https://maps.googleapis.com/maps/api/geocode/json",
            params={"address": address, "key": GOOGLE_MAPS_API_KEY},
        )
        geocode_resp.raise_for_status()
        geocode_data = geocode_resp.json()

        if geocode_data.get("status") != "OK" or not geocode_data.get("results"):
            raise HTTPException(
                status_code=404,
                detail=f"Endereço não encontrado (status: {geocode_data.get('status')}).",
            )

        location = geocode_data["results"][0]["geometry"]["location"]
        target_lat, target_lng = location["lat"], location["lng"]

        meta_resp = await http.get(
            "https://maps.googleapis.com/maps/api/streetview/metadata",
            params={
                "location": f"{target_lat},{target_lng}",
                "source": "outdoor",
                "key": GOOGLE_MAPS_API_KEY,
            },
        )
        meta_resp.raise_for_status()
        meta_data = meta_resp.json()

        if meta_data.get("status") != "OK":
            raise HTTPException(
                status_code=404,
                detail=f"Sem cobertura do Street View para este endereço (status: {meta_data.get('status')}).",
            )

        pano_location = meta_data["location"]
        heading = _bearing_deg(
            pano_location["lat"], pano_location["lng"], target_lat, target_lng
        )

        return {
            "target_lat": target_lat,
            "target_lng": target_lng,
            "pano_lat": pano_location["lat"],
            "pano_lng": pano_location["lng"],
            "pano_id": meta_data.get("pano_id"),
            "heading": heading,
        }


@app.get("/api/storefront")
async def storefront(address: str = Query(..., min_length=3)):
    """Resolve o endereço e retorna metadados (sem gastar a chamada de imagem)."""
    view = await _resolve_storefront_view(address)
    return {
        **view,
        "image_url": f"/api/storefront-image?address={address}",
    }


@app.get("/api/storefront-image")
async def storefront_image(
    address: str = Query(..., min_length=3),
    size: str = "640x400",
    fov: int = 80,
    pitch: int = 0,
):
    view = await _resolve_storefront_view(address)

    async with httpx.AsyncClient(timeout=15) as http:
        img_resp = await http.get(
            "https://maps.googleapis.com/maps/api/streetview",
            params={
                "size": size,
                "location": f"{view['target_lat']},{view['target_lng']}",
                "heading": view["heading"],
                "fov": fov,
                "pitch": pitch,
                "source": "outdoor",
                "key": GOOGLE_MAPS_API_KEY,
            },
        )
        img_resp.raise_for_status()

    return Response(content=img_resp.content, media_type="image/jpeg")


SCORE_SYSTEM_PROMPT = (
    "Você analisa fotos de fachadas capturadas do Google Street View para apoiar "
    "verificação de endereço. Responda SOMENTE com um JSON válido, sem markdown, "
    "no formato exato: "
    '{"classificacao": "comercial" | "residencial" | "indeterminado", '
    '"score_comercial": <inteiro 0-100, onde 100 = certamente comercial>, '
    '"porta_atendimento_visivel": true | false, '
    '"porta_aberta": true | false | null, '
    '"justificativa": "<até 2 frases em português>"}. '
    "score_comercial mede a probabilidade de ser um estabelecimento comercial "
    "(loja, restaurante, escritório, consultório etc.) e não uma residência. "
    "porta_atendimento_visivel indica se há uma porta de entrada voltada para a "
    "rua que pareça ser de atendimento ao público (vitrine, letreiro, entrada de "
    "loja). porta_aberta indica se essa porta está aberta na foto; use null se "
    "não houver porta de atendimento visível ou não for possível dizer."
)


async def _classify_storefront_image(image_bytes: bytes) -> dict:
    image_b64 = base64.b64encode(image_bytes).decode("ascii")
    data_url = f"data:image/jpeg;base64,{image_b64}"

    completion = client.chat.completions.create(
        model=VISION_MODEL,
        temperature=0,
        max_tokens=400,
        messages=[
            {"role": "system", "content": SCORE_SYSTEM_PROMPT},
            {
                "role": "user",
                "content": [
                    {
                        "type": "text",
                        "text": "Classifique esta fachada segundo as regras informadas.",
                    },
                    {"type": "image_url", "image_url": {"url": data_url}},
                ],
            },
        ],
    )

    raw = completion.choices[0].message.content or ""
    raw = raw.strip()
    if raw.startswith("```"):
        raw = raw.strip("`")
        if raw.lower().startswith("json"):
            raw = raw[4:]
        raw = raw.strip()

    try:
        result = json.loads(raw)
    except (json.JSONDecodeError, ValueError) as exc:
        raise HTTPException(
            status_code=502,
            detail=f"Resposta do modelo de visão não é um JSON válido: {exc}",
        ) from exc

    return result


@app.get("/api/storefront-score")
async def storefront_score(
    address: str = Query(..., min_length=3),
    size: str = "640x400",
    fov: int = 80,
    pitch: int = 0,
):
    """Busca a foto da fachada e retorna um score comercial x residencial."""
    view = await _resolve_storefront_view(address)

    async with httpx.AsyncClient(timeout=15) as http:
        img_resp = await http.get(
            "https://maps.googleapis.com/maps/api/streetview",
            params={
                "size": size,
                "location": f"{view['target_lat']},{view['target_lng']}",
                "heading": view["heading"],
                "fov": fov,
                "pitch": pitch,
                "source": "outdoor",
                "key": GOOGLE_MAPS_API_KEY,
            },
        )
        img_resp.raise_for_status()

    classification = await _classify_storefront_image(img_resp.content)

    return {
        **view,
        "image_url": f"/api/storefront-image?address={address}",
        **classification,
    }


frontend_dir = Path(__file__).resolve().parent / "frontend"
app.mount("/", StaticFiles(directory=frontend_dir, html=True), name="frontend")
