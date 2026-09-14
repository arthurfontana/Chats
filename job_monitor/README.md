# Monitor de vagas — Experian ("Gerente")

Script standalone (sem servidor) que verifica periodicamente a busca de vagas
`https://jobs.experian.com/jobs?options=765&q=Gerente&...` e detecta vagas novas.

## Como funciona

- `check_experian_jobs.py` busca todas as páginas de resultado da pesquisa e
  extrai `(id da vaga, título, link)` de cada uma.
- Compara com `seen_jobs.json` (estado da última execução, versionado neste
  repositório) para descobrir quais vagas são novas desde a última checagem.
- Atualiza `seen_jobs.json` com o resultado atual (vagas que saíram da busca —
  encerradas/preenchidas — são removidas do estado).
- Imprime em stdout um JSON com `new_jobs` (lista vazia na primeira execução,
  já que ali só se define o baseline).

## Automação

Este monitoramento roda automaticamente 1x por dia via uma Routine agendada
do Claude (não depende de infraestrutura própria: nenhum cron/servidor deste
repositório precisa estar no ar). A cada execução:

1. Roda `python job_monitor/check_experian_jobs.py`.
2. Se houver vagas novas: envia e-mail para o dono do monitoramento e avisa na
   conversa do Claude Code.
3. Faz commit + push de `seen_jobs.json` atualizado nesta branch.

Para rodar manualmente:

```bash
python job_monitor/check_experian_jobs.py
```
