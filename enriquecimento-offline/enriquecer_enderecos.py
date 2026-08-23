"""
Enriquecimento de endereços (comercial x residencial) via geocodificação + Street View + IA de visão.

Uso:
    python enriquecer_enderecos.py --entrada enderecos.csv --saida enderecos_enriquecidos.csv

Configuração (config.env, mesma pasta do script):
    GOOGLE_MAPS_API_KEY=...
    VISION_API_KEY=...
    VISION_BASE_URL=https://api.openai.com/v1      # ou outro endpoint compatível OpenAI
    VISION_MODEL=gpt-4o-mini
    CONCURRENCY=5

O script NÃO depende de nenhum servidor/página web: roda 100% localmente,
lendo um CSV e gravando outro CSV enriquecido. As únicas chamadas de rede são
para a API do Google Maps (geocodificação/Street View) e para a API do modelo
de visão configurado — ambas necessárias para "ver" a fachada do endereço.
"""

from __future__ import annotations

import argparse
import asyncio
import csv
import json
import math
import os
import sys
from pathlib import Path
from typing import Any

import httpx
from openai import AsyncOpenAI

# --------------------------------------------------------------------------
# Configuração
# --------------------------------------------------------------------------

SCRIPT_DIR = Path(__file__).resolve().parent


def carregar_config() -> dict[str, str]:
    config: dict[str, str] = {}
    config_path = SCRIPT_DIR / "config.env"
    if config_path.exists():
        for linha in config_path.read_text(encoding="utf-8").splitlines():
            linha = linha.strip()
            if not linha or linha.startswith("#") or "=" not in linha:
                continue
            chave, valor = linha.split("=", 1)
            config[chave.strip()] = valor.strip()
    # Variáveis de ambiente têm prioridade sobre o config.env
    for chave in ("GOOGLE_MAPS_API_KEY", "VISION_API_KEY", "VISION_BASE_URL", "VISION_MODEL", "CONCURRENCY"):
        if os.environ.get(chave):
            config[chave] = os.environ[chave]
    return config


CONFIG = carregar_config()
GOOGLE_MAPS_API_KEY = CONFIG.get("GOOGLE_MAPS_API_KEY", "")
VISION_API_KEY = CONFIG.get("VISION_API_KEY", "")
VISION_BASE_URL = CONFIG.get("VISION_BASE_URL", "https://api.openai.com/v1")
VISION_MODEL = CONFIG.get("VISION_MODEL", "gpt-4o-mini")
CONCURRENCY = int(CONFIG.get("CONCURRENCY", "5"))

GEOCODE_URL = "https://maps.googleapis.com/maps/api/geocode/json"
STREETVIEW_META_URL = "https://maps.googleapis.com/maps/api/streetview/metadata"
STREETVIEW_IMG_URL = "https://maps.googleapis.com/maps/api/streetview"

COLUNAS_CANDIDATAS_ENDERECO = ["endereco", "endereço", "address", "logradouro"]

# --------------------------------------------------------------------------
# Prompt de classificação — recalibrado para produzir confiança gradual
# --------------------------------------------------------------------------

SCORE_SYSTEM_PROMPT = """\
Você é um analista treinado para classificar fachadas de endereços como
"comercial", "residencial" ou "indeterminado" a partir de uma imagem de
Street View.

Responda SOMENTE com um JSON válido no formato:
{
  "classificacao": "comercial" | "residencial" | "indeterminado",
  "score_comercial": <inteiro 0-100>,
  "confianca": "alta" | "media" | "baixa",
  "porta_atendimento_visivel": true | false,
  "porta_aberta": true | false | null,
  "motivo_incerteza": "<string curta ou null>",
  "justificativa": "<até 2 frases em português>"
}

IMPORTANTE sobre score_comercial: é uma ESCALA DE PROBABILIDADE, não um
rótulo binário. Use âncoras como guia e NÃO arredonde para 0 ou 100 a
menos que a evidência seja realmente inequívoca:
  - 95-100: letreiro comercial claro, vitrine, horário de funcionamento visível
  - 70-94: fachada com forte indício comercial, mas sem certeza total
    (ex.: portão fechado, letreiro parcialmente visível, sem pessoas)
  - 45-69: sinais mistos ou ambíguos (ex.: pode ser home office, garagem
    comercial em rua residencial, imagem antiga/desatualizada)
  - 20-44: parece residencial, mas com algum traço que gera dúvida
  - 0-19: claramente residencial (portão de casa, sem letreiro, jardim, etc.)

Reduza "confianca" para "media" ou "baixa" sempre que houver qualquer um
destes fatores, e explique em "motivo_incerteza":
  - imagem de baixa qualidade, desatualizada, ângulo ruim ou objeto
    bloqueando a fachada
  - endereço geocodificado com baixa precisão (rooftop incerto,
    aproximado por interpolação)
  - painorama do Street View distante do ponto exato do endereço
  - fachada sem elementos claros de uso comercial nem residencial

Se não for possível obter imagem ou o endereço não puder ser localizado,
use "classificacao": "indeterminado", "score_comercial": 50,
"confianca": "baixa" e explique o motivo em "motivo_incerteza".
"""


def _bearing_deg(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    lat1_r, lat2_r = math.radians(lat1), math.radians(lat2)
    dlon = math.radians(lon2 - lon1)
    x = math.sin(dlon) * math.cos(lat2_r)
    y = math.cos(lat1_r) * math.sin(lat2_r) - math.sin(lat1_r) * math.cos(lat2_r) * math.cos(dlon)
    return (math.degrees(math.atan2(x, y)) + 360) % 360


async def _geocode(client: httpx.AsyncClient, endereco: str) -> dict[str, Any] | None:
    resp = await client.get(GEOCODE_URL, params={"address": endereco, "key": GOOGLE_MAPS_API_KEY, "language": "pt-BR"})
    resp.raise_for_status()
    data = resp.json()
    resultados = data.get("results") or []
    if not resultados:
        return None
    resultado = resultados[0]
    location = resultado["geometry"]["location"]
    return {
        "lat": location["lat"],
        "lng": location["lng"],
        "endereco_formatado": resultado.get("formatted_address", endereco),
        "location_type": resultado["geometry"].get("location_type", ""),
    }


async def _resolve_streetview(client: httpx.AsyncClient, lat: float, lng: float) -> dict[str, Any] | None:
    meta_resp = await client.get(
        STREETVIEW_META_URL,
        params={"location": f"{lat},{lng}", "key": GOOGLE_MAPS_API_KEY},
    )
    meta_resp.raise_for_status()
    meta = meta_resp.json()
    if meta.get("status") != "OK":
        return None
    pano_lat = meta["location"]["lat"]
    pano_lng = meta["location"]["lng"]
    heading = _bearing_deg(pano_lat, pano_lng, lat, lng)
    img_resp = await client.get(
        STREETVIEW_IMG_URL,
        params={
            "size": "640x400",
            "location": f"{lat},{lng}",
            "heading": heading,
            "fov": 80,
            "pitch": 0,
            "key": GOOGLE_MAPS_API_KEY,
        },
    )
    img_resp.raise_for_status()
    return {"bytes": img_resp.content, "heading": heading}


async def _classificar_imagem(vision_client: AsyncOpenAI, image_bytes: bytes) -> dict[str, Any]:
    import base64

    b64 = base64.b64encode(image_bytes).decode("ascii")
    resposta = await vision_client.chat.completions.create(
        model=VISION_MODEL,
        messages=[
            {"role": "system", "content": SCORE_SYSTEM_PROMPT},
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": "Classifique esta fachada."},
                    {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{b64}"}},
                ],
            },
        ],
        response_format={"type": "json_object"},
        temperature=0,
    )
    return json.loads(resposta.choices[0].message.content)


async def enriquecer_endereco(
    http_client: httpx.AsyncClient, vision_client: AsyncOpenAI, endereco: str
) -> dict[str, Any]:
    base = {
        "endereco": endereco,
        "classificacao": "indeterminado",
        "score_comercial": 50,
        "confianca": "baixa",
        "porta_atendimento_visivel": None,
        "porta_aberta": None,
        "motivo_incerteza": None,
        "justificativa": "",
        "erro": "",
    }
    try:
        geo = await _geocode(http_client, endereco)
        if geo is None:
            base["motivo_incerteza"] = "Endereço não encontrado na geocodificação."
            base["erro"] = "geocodificacao_falhou"
            return base

        if geo["location_type"] not in ("ROOFTOP", "RANGE_INTERPOLATED"):
            baixa_precisao = True
        else:
            baixa_precisao = False

        sv = await _resolve_streetview(http_client, geo["lat"], geo["lng"])
        if sv is None:
            base["motivo_incerteza"] = "Sem imagem de Street View disponível para este ponto."
            base["erro"] = "streetview_indisponivel"
            base["endereco"] = geo["endereco_formatado"]
            return base

        classificacao = await _classificar_imagem(vision_client, sv["bytes"])
        base.update(classificacao)
        base["endereco"] = geo["endereco_formatado"]

        if baixa_precisao and base.get("confianca") == "alta":
            base["confianca"] = "media"
            motivo = base.get("motivo_incerteza") or ""
            extra = "Geocodificação com precisão aproximada (não é rooftop)."
            base["motivo_incerteza"] = f"{motivo} {extra}".strip()

        return base
    except Exception as exc:  # isolamento de erro por linha
        base["erro"] = f"{type(exc).__name__}: {exc}"
        base["motivo_incerteza"] = "Falha técnica ao processar este endereço."
        return base


def detectar_coluna_endereco(fieldnames: list[str]) -> str:
    normalizados = {c.lower().strip(): c for c in fieldnames}
    for candidata in COLUNAS_CANDIDATAS_ENDERECO:
        if candidata in normalizados:
            return normalizados[candidata]
    return fieldnames[0]


async def processar_csv(caminho_entrada: Path, caminho_saida: Path) -> None:
    with caminho_entrada.open("r", encoding="utf-8-sig", newline="") as f:
        leitor = csv.DictReader(f)
        linhas = list(leitor)
        fieldnames_originais = leitor.fieldnames or []

    if not linhas:
        print("CSV de entrada está vazio.")
        return

    coluna_endereco = detectar_coluna_endereco(fieldnames_originais)
    print(f"Coluna de endereço detectada: '{coluna_endereco}' ({len(linhas)} linhas)")

    semaforo = asyncio.Semaphore(CONCURRENCY)
    resultados: list[dict[str, Any]] = [None] * len(linhas)  # type: ignore[list-item]

    async with httpx.AsyncClient(timeout=30) as http_client:
        vision_client = AsyncOpenAI(api_key=VISION_API_KEY, base_url=VISION_BASE_URL)

        async def processar_linha(indice: int, linha: dict[str, str]) -> None:
            async with semaforo:
                endereco = (linha.get(coluna_endereco) or "").strip()
                if not endereco:
                    resultados[indice] = {**linha, "erro": "endereco_vazio"}
                    return
                enriquecido = await enriquecer_endereco(http_client, vision_client, endereco)
                resultados[indice] = {**linha, **enriquecido}
                print(f"[{indice + 1}/{len(linhas)}] {endereco[:60]!r} -> "
                      f"{enriquecido.get('classificacao')} "
                      f"(score={enriquecido.get('score_comercial')}, "
                      f"confianca={enriquecido.get('confianca')})")

        await asyncio.gather(*(processar_linha(i, linha) for i, linha in enumerate(linhas)))

    colunas_novas = [
        "classificacao", "score_comercial", "confianca",
        "porta_atendimento_visivel", "porta_aberta",
        "motivo_incerteza", "justificativa", "erro",
    ]
    fieldnames_saida = fieldnames_originais + [c for c in colunas_novas if c not in fieldnames_originais]

    with caminho_saida.open("w", encoding="utf-8-sig", newline="") as f:
        escritor = csv.DictWriter(f, fieldnames=fieldnames_saida, extrasaction="ignore")
        escritor.writeheader()
        for linha in resultados:
            escritor.writerow(linha)

    print(f"\nConcluído. Arquivo gerado em: {caminho_saida}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Enriquecimento de endereços (comercial x residencial).")
    parser.add_argument("--entrada", default="enderecos.csv", help="CSV de entrada (padrão: enderecos.csv)")
    parser.add_argument("--saida", default="enderecos_enriquecidos.csv", help="CSV de saída")
    args = parser.parse_args()

    if not GOOGLE_MAPS_API_KEY or not VISION_API_KEY:
        print("ERRO: configure GOOGLE_MAPS_API_KEY e VISION_API_KEY em config.env antes de rodar.")
        sys.exit(1)

    caminho_entrada = Path(args.entrada)
    if not caminho_entrada.exists():
        print(f"ERRO: arquivo de entrada não encontrado: {caminho_entrada}")
        sys.exit(1)

    asyncio.run(processar_csv(caminho_entrada, Path(args.saida)))


if __name__ == "__main__":
    main()
