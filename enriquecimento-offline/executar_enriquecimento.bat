@echo off
setlocal

REM ============================================================
REM  Enriquecimento de enderecos - execucao local
REM  EDITE a linha ARQUIVO_ENTRADA abaixo apontando para o seu CSV
REM ============================================================

set ARQUIVO_ENTRADA=enderecos.csv
set ARQUIVO_SAIDA=enderecos_enriquecidos.csv

cd /d "%~dp0"

where python >nul 2>nul
if errorlevel 1 (
    echo Python nao encontrado no PATH. Instale o Python 3.10+ e tente novamente.
    pause
    exit /b 1
)

if not exist "venv\" (
    echo Criando ambiente virtual...
    python -m venv venv
)

call venv\Scripts\activate.bat

echo Instalando/atualizando dependencias...
pip install -q -r requirements.txt

if not exist "config.env" (
    echo.
    echo ATENCAO: arquivo config.env nao encontrado.
    echo Copie config.env.exemplo para config.env e preencha suas chaves de API.
    pause
    exit /b 1
)

if not exist "%ARQUIVO_ENTRADA%" (
    echo.
    echo ATENCAO: arquivo de entrada "%ARQUIVO_ENTRADA%" nao encontrado nesta pasta.
    pause
    exit /b 1
)

echo.
echo Processando %ARQUIVO_ENTRADA% ...
python enriquecer_enderecos.py --entrada "%ARQUIVO_ENTRADA%" --saida "%ARQUIVO_SAIDA%"

echo.
echo Concluido. Resultado em: %ARQUIVO_SAIDA%
pause
