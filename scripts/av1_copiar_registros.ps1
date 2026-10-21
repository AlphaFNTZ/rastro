# Copia os registros gravados pelo Rastro no celular (via USB/adb) para docs\evidencias\dados.
#
# Uso, na raiz do projeto, com o celular conectado e a depuração USB autorizada:
#   powershell -ExecutionPolicy Bypass -File scripts\av1_copiar_registros.ps1 -Ensaio estatico -Sessao 20261021_183000
#   powershell -ExecutionPolicy Bypass -File scripts\av1_copiar_registros.ps1 -Listar
#
#   -Ensaio : estatico | movimento | bloqueado  (pasta de destino)
#   -Sessao : carimbo AAAAMMDD_HHMMSS da sessão (o mesmo nos arquivos gnss_ e aquisicao_)
#
# Sem adb: copie pelo Explorador de Arquivos, em
#   <celular>\Android\data\com.example.rastro\files\registros
# Atenção: os registros têm posição real. Grave em local público (campus), não em casa;
# o repositório é público no GitHub.
param(
    [ValidateSet("estatico", "movimento", "bloqueado")] [string]$Ensaio,
    [string]$Sessao,
    [switch]$Listar
)
$raiz = Split-Path -Parent $PSScriptRoot
$origem = "/sdcard/Android/data/com.example.rastro/files/registros"
$adb = "adb"
if ($env:ANDROID_HOME) { $adb = Join-Path $env:ANDROID_HOME "platform-tools\adb.exe" }
elseif (Test-Path "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe") { $adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" }

if ($Listar -or -not $Ensaio -or -not $Sessao) {
    Write-Host "Registros no celular ($origem):"
    & $adb shell ls -l $origem
    if (-not $Listar) { Write-Host "Informe -Ensaio e -Sessao para copiar." }
    exit 0
}
$destino = Join-Path $raiz "docs\evidencias\dados\$Ensaio"
New-Item -ItemType Directory -Force -Path $destino | Out-Null
foreach ($prefixo in @("gnss_", "aquisicao_")) {
    $nome = "$prefixo$Sessao.csv"
    & $adb pull "$origem/$nome" (Join-Path $destino $nome)
    if ($LASTEXITCODE -ne 0) { Write-Host "Não encontrado: $nome" }
}
Write-Host "Copiado para $destino"
