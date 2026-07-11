import os
from pathlib import Path
from typing import List

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from fastapi.staticfiles import StaticFiles
from openai import OpenAI
from pydantic import BaseModel

load_dotenv(Path(__file__).resolve().parent / ".env")

API_KEY = os.getenv("NVIDIA_API_KEY")
BASE_URL = os.getenv("NVIDIA_BASE_URL", "https://integrate.api.nvidia.com/v1")
MODEL = os.getenv("GLM_MODEL", "z-ai/glm-5.2")
SYSTEM_PROMPT = "Você é um assistente útil, direto e honesto. Responda em português, a menos que o usuário escreva em outro idioma."

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


frontend_dir = Path(__file__).resolve().parent / "frontend"
app.mount("/", StaticFiles(directory=frontend_dir, html=True), name="frontend")
