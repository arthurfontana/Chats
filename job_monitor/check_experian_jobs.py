"""Monitora novas vagas no portal de carreiras da Experian para um filtro de busca fixo.

Uso:
    python job_monitor/check_experian_jobs.py

Compara as vagas encontradas agora com `job_monitor/seen_jobs.json` (estado da
última execução, versionado no repo) e imprime em stdout um JSON com as vagas
novas (se houver). Sempre atualiza o arquivo de estado com a lista completa
de vagas vistas na execução atual.
"""

import json
import re
import sys
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

SEARCH_URL = "https://jobs.experian.com/jobs"
SEARCH_PARAMS = "options=765&q=Gerente&ln=&la=0&lo=0&lr=96&li="
BASE_JOB_URL = "https://jobs.experian.com"

STATE_FILE = Path(__file__).resolve().parent / "seen_jobs.json"

TILE_TITLE_RE = re.compile(
    r'attrax-vacancy-tile__title[^"]*"\s+href="(?P<href>/job/[^"]+-jid-(?P<jid>\d+))"'
    r'[^>]*>(?P<title>[^<]+)<',
)


def fetch_page(page: int) -> str:
    url = f"{SEARCH_URL}?{SEARCH_PARAMS}&page={page}"
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return resp.read().decode("utf-8", errors="replace")


def fetch_all_jobs() -> dict:
    """Retorna {jid: {"title":..., "url":...}} para todas as páginas de resultado."""
    jobs = {}
    page = 1
    while True:
        html = fetch_page(page)
        matches = list(TILE_TITLE_RE.finditer(html))
        if not matches:
            break
        new_on_page = 0
        for m in matches:
            jid = m.group("jid")
            if jid in jobs:
                continue
            jobs[jid] = {
                "title": m.group("title").strip(),
                "url": BASE_JOB_URL + m.group("href"),
            }
            new_on_page += 1
        if new_on_page == 0:
            break
        page += 1
        if page > 20:  # trava de segurança
            break
    return jobs


def load_state() -> dict:
    if STATE_FILE.exists():
        return json.loads(STATE_FILE.read_text(encoding="utf-8"))
    return {}


def save_state(jobs: dict) -> None:
    now = datetime.now(timezone.utc).isoformat()
    state = load_state()
    for jid, data in jobs.items():
        if jid in state:
            state[jid]["title"] = data["title"]
            state[jid]["url"] = data["url"]
        else:
            state[jid] = {**data, "first_seen": now}
    # remove vagas que saíram do resultado (encerradas/preenchidas)
    for jid in list(state.keys()):
        if jid not in jobs:
            del state[jid]
    STATE_FILE.write_text(
        json.dumps(state, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def main() -> None:
    current_jobs = fetch_all_jobs()
    previous_state = load_state()
    is_first_run = len(previous_state) == 0

    new_jobs = [
        {"jid": jid, **data}
        for jid, data in current_jobs.items()
        if jid not in previous_state
    ]

    save_state(current_jobs)

    result = {
        "checked_at": datetime.now(timezone.utc).isoformat(),
        "total_jobs_found": len(current_jobs),
        "is_first_run": is_first_run,
        # No primeiro run não há "novidade" real, é só o baseline inicial.
        "new_jobs": [] if is_first_run else new_jobs,
    }
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
