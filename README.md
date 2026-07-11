# Chats

Chat web (estilo Claude) conectado à API do GLM 5.2 via NVIDIA Integrate (`https://integrate.api.nvidia.com/v1`).

## Arquitetura

- `backend/` — servidor FastAPI que guarda a chave da API em variável de ambiente, chama o modelo `z-ai/glm-5.2` e faz streaming da resposta para o navegador. A chave **nunca** é exposta ao cliente.
- `frontend/` — página estática (HTML/CSS/JS) com sidebar de conversas, renderização de markdown/código e streaming em tempo real, servida pelo próprio backend.

## Como rodar

```bash
cd backend
python -m venv .venv
source .venv/bin/activate       # Windows: .venv\Scripts\activate
pip install -r requirements.txt

cp .env.example .env
# edite backend/.env e preencha NVIDIA_API_KEY com sua chave

uvicorn server:app --reload --port 8000
```

Abra `http://localhost:8000` no navegador.

## ⚠️ Segurança

Nunca faça commit do arquivo `backend/.env` (já está no `.gitignore`). Se uma chave de API for exposta publicamente (ex.: colada em um chat, commit, print), revogue-a e gere uma nova no painel do provedor.
