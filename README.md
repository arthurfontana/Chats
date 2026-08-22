# Chats

Chat web (estilo Claude) conectado à API do Llama 3.3 70B via NVIDIA Integrate (`https://integrate.api.nvidia.com/v1`).

## Arquitetura

- `main.py` — app FastAPI (na raiz do projeto, formato zero-config esperado pela Vercel) que guarda a chave da API em variável de ambiente, chama o modelo `meta/llama-3.3-70b-instruct` e faz streaming da resposta para o navegador. A chave **nunca** é exposta ao cliente.
- `frontend/` — página estática (HTML/CSS/JS) com sidebar de conversas, renderização de markdown/código e streaming em tempo real, servida pelo próprio `main.py`.
- `frontend/storefront.html` — página simples para digitar um endereço e ver a foto da fachada do estabelecimento (Street View). Consome `/api/storefront-image`.
- `frontend/storefront-csv.html` — upload de um CSV com endereços e processamento em lote: cada linha recebe score comercial e classificação. Consome `/api/storefront-batch`.
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
   - `GLM_MODEL` (opcional, default já é `meta/llama-3.3-70b-instruct`)
   - `GOOGLE_MAPS_API_KEY` (obrigatória para a página `/storefront.html`) — chave da Google Cloud com **Geocoding API** e **Street View Static API** habilitadas (e faturamento ativo no projeto Google Cloud). Nunca é exposta ao cliente: todas as chamadas ao Google Maps acontecem no backend.

   Ou via CLI: `vercel env add NVIDIA_API_KEY` / `vercel env add GOOGLE_MAPS_API_KEY`.
3. **Deploy.** A Vercel builda e serve tanto a API (`/api/chat`, streaming) quanto os arquivos estáticos de `frontend/` a partir da mesma função.

Detalhes que já vêm resolvidos no repo:
- `vercel.json` define `maxDuration: 60` para a função, porque respostas longas do modelo (até `max_tokens=16384`) podem levar mais que os 10s padrão do plano Hobby.
- Streaming funciona nativamente no runtime Python da Vercel (Fluid Compute), sem configuração extra.

Nada além disso é necessário — não precisa de banco de dados, build step, nem configurar rewrites manualmente.

## Foto da fachada (Street View)

Acesse `/storefront.html` no deploy (ou `http://localhost:8000/storefront.html` local), digite o endereço completo do estabelecimento e a página busca a foto da fachada.

Fluxo no backend (`main.py`):
1. `GET /api/storefront?address=...` — geocodifica o endereço, busca o metadata do Street View mais próximo e calcula o `heading` (ângulo da câmera) apontando do ponto do panorama para o endereço alvo. Retorna JSON com as coordenadas e a URL da imagem.
2. `GET /api/storefront-image?address=...` — repete a resolução acima e retorna os bytes da imagem (`image/jpeg`) direto do Street View Static API, já com o heading correto.

A chave `GOOGLE_MAPS_API_KEY` fica só no servidor; o navegador nunca vê a chave, apenas o endereço da própria API (`/api/storefront-image`).

## Score em lote via CSV

Acesse `/storefront-csv.html` no deploy (ou `http://localhost:8000/storefront-csv.html` local), envie um arquivo `.csv` com uma coluna de endereço completo (`endereco`, `endereço`, `address` ou `logradouro` — se nenhuma dessas existir, usa a primeira coluna) e a página processa cada linha e mostra uma tabela com score comercial, classificação e a justificativa, com opção de baixar o resultado em CSV.

Fluxo no backend (`main.py`):
1. `POST /api/storefront-batch` — recebe o arquivo (`multipart/form-data`, campo `file`), detecta a coluna de endereço, e para cada linha repete o fluxo geocodificação → foto do Street View → classificação por visão computacional (mesma lógica de `/api/storefront-score`), com até 5 endereços em paralelo. Linhas que falham (endereço não encontrado, sem cobertura do Street View etc.) voltam com um campo `erro` em vez de derrubar o lote inteiro.

## ⚠️ Segurança

Nunca faça commit do arquivo `.env` (já está no `.gitignore`). Se uma chave de API for exposta publicamente (ex.: colada em um chat, commit, print), revogue-a e gere uma nova no painel do provedor.
