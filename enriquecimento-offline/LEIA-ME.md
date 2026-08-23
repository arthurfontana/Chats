# Enriquecimento de endereços — execução local (offline de página web)

Processo que roda inteiramente na sua máquina corporativa: você edita o
`.bat`, aponta para o CSV com os endereços, dá dois cliques, e recebe um
CSV enriquecido com classificação comercial/residencial e um score
calibrado (não mais binário 0/100).

## Arquivos

- `enriquecer_enderecos.py` — script principal (lê CSV, geocodifica, busca
  imagem do Street View, classifica com IA de visão, grava CSV de saída).
- `executar_enriquecimento.bat` — o que você roda. Cria o ambiente Python,
  instala as dependências e executa o script.
- `config.env.exemplo` — modelo de configuração (chaves de API).
- `requirements.txt` — dependências Python (`httpx`, `openai`).
- `enderecos.csv` — exemplo do formato esperado (2 linhas). Substitua pelo
  seu arquivo real ou aponte o `.bat` para o seu.

## Passo a passo

1. **Copie esta pasta** para a máquina corporativa (ela tem Python
   disponível, então nada mais é necessário instalar manualmente — o
   `.bat` cuida do ambiente virtual e das dependências).

2. **Configure as chaves de API**:
   - Copie `config.env.exemplo` para `config.env`.
   - Preencha `GOOGLE_MAPS_API_KEY` (usada para geocodificar o endereço e
     buscar a imagem de Street View) e `VISION_API_KEY` +
     `VISION_BASE_URL` + `VISION_MODEL` (o modelo de visão que classifica
     a fachada — pode ser OpenAI, Azure OpenAI ou um provedor interno da
     empresa, desde que exponha uma API compatível com o padrão OpenAI).
   - **Nunca** coloque `config.env` em nenhum repositório Git — ele tem
     segredos.

3. **Coloque seu CSV** de endereços na mesma pasta (ou em outro caminho).
   O script detecta automaticamente a coluna de endereço, procurando por
   `endereco`, `endereço`, `address` ou `logradouro`; se não achar nenhuma
   dessas, usa a primeira coluna do arquivo.

4. **Edite o `.bat`** se o nome do seu arquivo for diferente do padrão:
   ```bat
   set ARQUIVO_ENTRADA=enderecos.csv
   set ARQUIVO_SAIDA=enderecos_enriquecidos.csv
   ```
   Troque para o nome do seu arquivo de bases internas.

5. **Dê dois cliques em `executar_enriquecimento.bat`**. Na primeira
   execução ele cria um ambiente virtual (`venv`) e instala as
   dependências — pode demorar um pouco. Nas próximas, é rápido.

6. O resultado sai em `enderecos_enriquecidos.csv` (ou o nome que você
   definiu), com as colunas originais preservadas e mais:

   | Coluna | Significado |
   |---|---|
   | `classificacao` | `comercial`, `residencial` ou `indeterminado` |
   | `score_comercial` | 0–100, probabilidade calibrada de ser comercial (não é mais só 0 ou 100 — veja abaixo) |
   | `confianca` | `alta`, `media` ou `baixa` — o quanto o modelo confia no score |
   | `motivo_incerteza` | texto explicando por que a confiança é média/baixa (imagem ruim, geocodificação imprecisa, endereço não localizado etc.) |
   | `porta_atendimento_visivel` / `porta_aberta` | pistas visuais extraídas da fachada |
   | `justificativa` | explicação curta da classificação |
   | `erro` | preenchido quando algo falhou nessa linha específica (não interrompe o restante do lote) |

## Sobre o score deixar de ser 0/100

O prompt original pedia "0-100, onde 100 = certamente comercial" sem
âncoras — na prática o modelo quase sempre respondia nos extremos. O
prompt novo (`SCORE_SYSTEM_PROMPT` dentro do script) dá faixas explícitas
(95-100, 70-94, 45-69, 20-44, 0-19) e pede um campo `confianca` separado,
junto com `motivo_incerteza` sempre que houver:

- imagem de baixa qualidade, desatualizada ou com ângulo ruim;
- geocodificação sem precisão "rooftop" (endereço aproximado);
- painorama do Street View distante do ponto exato;
- fachada sem sinais claros de uso comercial nem residencial.

Isso te dá dois eixos para decidir: **o que** o modelo acha (score) e **o
quanto ele confia nisso** (confianca + motivo_incerteza) — útil para
priorizar revisão manual só nos casos incertos, em vez de tratar tudo como
certeza absoluta.

## Processando lotes grandes

Não há mais o limite de 20 linhas por chamada que existia na versão web
(criado por causa do timeout de serverless/Vercel). O script processa o
CSV inteiro de uma vez, com `CONCURRENCY` linhas em paralelo
(configurável em `config.env`, padrão 5 — evite valores muito altos para
não estourar limite de requisições das APIs). Para bases muito grandes
(dezenas de milhares de linhas), considere dividir o CSV em partes e rodar
em lotes, para poder interromper e retomar sem perder o progresso já
feito.
