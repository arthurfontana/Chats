# Chats

Chat web (estilo Claude) conectado à API do GLM 5.2 via NVIDIA Integrate (`https://integrate.api.nvidia.com/v1`).

## Arquitetura

- `main.py` — app FastAPI (na raiz do projeto, formato zero-config esperado pela Vercel) que guarda a chave da API em variável de ambiente, chama o modelo `z-ai/glm-5.2` e faz streaming da resposta para o navegador. A chave **nunca** é exposta ao cliente.
- `frontend/` — página estática (HTML/CSS/JS) com sidebar de conversas, renderização de markdown/código e streaming em tempo real, servida pelo próprio `main.py`.
- `android/` — projeto Android separado (Gradle/Kotlin) que agenda o envio automático de mensagens no app oficial do Claude via Accessibility Service. Não depende do chat web acima; veja `android/README.md` para detalhes, limitações e como baixar o APK direto da aba Actions do GitHub.

## Como rodar localmente

```bash
python -m venv .venv
source .venv/bin/activate       # Windows: .venv\Scripts\activate
pip install -r requirements.txt

cp .env.example .env
# edite .env e preencha NVIDIA_API_KEY com sua chave

uvicorn main:app --reload --port 8000
```

Abra `http://localhost:8000` no navegador.

## Deploy na Vercel

O projeto já está no formato "zero-config" que a Vercel reconhece automaticamente para apps FastAPI (arquivo `main.py` com a variável `app` na raiz, `requirements.txt` na raiz, `.python-version` fixando a versão do Python). Não é preciso reescrever nada — só:

1. **Criar o projeto na Vercel** apontando para este repositório (Import Project → selecione o repo). A Vercel detecta o Python/FastAPI automaticamente, sem precisar escolher framework manualmente.
2. **Configurar as variáveis de ambiente** em *Project Settings → Environment Variables* (isso é obrigatório — o `.env` não vai para o Git nem para o deploy):
   - `NVIDIA_API_KEY` (obrigatória) — sua chave da NVIDIA. **Use uma chave nova**, não a que foi exposta durante o desenvolvimento.
   - `NVIDIA_BASE_URL` (opcional, default já é `https://integrate.api.nvidia.com/v1`)
   - `GLM_MODEL` (opcional, default já é `z-ai/glm-5.2`)
   
   Ou via CLI: `vercel env add NVIDIA_API_KEY`.
3. **Deploy.** A Vercel builda e serve tanto a API (`/api/chat`, streaming) quanto os arquivos estáticos de `frontend/` a partir da mesma função.

Detalhes que já vêm resolvidos no repo:
- `vercel.json` define `maxDuration: 60` para a função, porque respostas longas do modelo (até `max_tokens=16384`) podem levar mais que os 10s padrão do plano Hobby.
- Streaming funciona nativamente no runtime Python da Vercel (Fluid Compute), sem configuração extra.

Nada além disso é necessário — não precisa de banco de dados, build step, nem configurar rewrites manualmente.

## ⚠️ Segurança

Nunca faça commit do arquivo `.env` (já está no `.gitignore`). Se uma chave de API for exposta publicamente (ex.: colada em um chat, commit, print), revogue-a e gere uma nova no painel do provedor.
