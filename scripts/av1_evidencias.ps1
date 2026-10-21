# Gera as evidências automatizadas da AV1 (GAT108) a partir da raiz do repositório.
#
#   1. roda a suíte de testes de unidade e guarda a saída do comando;
#   2. roda o teste de corrida (CircularImuBufferConcurrencyTest) DEZ vezes, cada uma
#      do zero (--rerun-tasks), e monta a tabela com uma linha por execução;
#   3. roda EvidenciasAv1Test, que lê docs\evidencias\dados e grava as tabelas de
#      resíduo, detector, sensibilidade, ensaio estático e aquisição em docs\evidencias\resultados.
#
# Uso (PowerShell, na raiz do projeto):
#   powershell -ExecutionPolicy Bypass -File scripts\av1_evidencias.ps1
#
# Nada é enviado para fora do computador. Os arquivos gerados citam o commit e o comando.

$ErrorActionPreference = "Continue"
$raiz = Split-Path -Parent $PSScriptRoot
Set-Location $raiz
$saida = Join-Path $raiz "docs\evidencias\resultados"
New-Item -ItemType Directory -Force -Path $saida | Out-Null
$commit = (git rev-parse --short HEAD) 2>$null
if (-not $commit) { $commit = "desconhecido" }
$alteracoes = (git status --porcelain) 2>$null
$estado = if ($alteracoes) { "com alterações não comitadas" } else { "árvore limpa" }
$maquina = "$env:COMPUTERNAME / $([System.Environment]::OSVersion.VersionString)"
$autor = (git config user.email) 2>$null
$xmlCorrida = Join-Path $raiz "app\build\test-results\testDebugUnitTest\TEST-com.example.rastro.s2.CircularImuBufferConcurrencyTest.xml"

function Escrever($arquivo, $texto) {
    [System.IO.File]::WriteAllText((Join-Path $saida $arquivo), $texto, (New-Object System.Text.UTF8Encoding($false)))
}

# 1. Suíte completa -------------------------------------------------------------
Write-Host "== 1/3 Suíte de testes de unidade"
$inicio = Get-Date
$log = & .\gradlew.bat :app:testDebugUnitTest --rerun-tasks --console=plain 2>&1 | Out-String
$codigo = $LASTEXITCODE
$fim = Get-Date
$resumo = @()
Get-ChildItem "app\build\test-results\testDebugUnitTest\TEST-*.xml" -ErrorAction SilentlyContinue | ForEach-Object {
    $s = ([xml](Get-Content $_.FullName -Raw)).testsuite
    $resumo += "| $($s.name) | $($s.tests) | $($s.failures) | $($s.errors) | $($s.skipped) | $($s.time) |"
}
$md = "# Saída do comando de teste`n`n"
$md += "- Comando: ``.\gradlew.bat :app:testDebugUnitTest --rerun-tasks```n"
$md += "- Commit: ``$commit`` ($estado) · executado por $autor em $maquina`n"
$md += "- Início: $($inicio.ToString('dd/MM/yyyy HH:mm:ss')) · fim: $($fim.ToString('HH:mm:ss')) · código de saída: $codigo`n`n"
$md += "| Classe | Testes | Falhas | Erros | Ignorados | Tempo (s) |`n|---|---|---|---|---|---|`n"
$md += ($resumo -join "`n") + "`n`n"
$md += "Testes ignorados em EvidenciasAv1Test significam que aquele registro físico ainda não foi copiado para docs\evidencias\dados.`n`n"
$md += "``````text`n" + $log + "`n```````n"
Escrever "testes_unidade.md" $md
Write-Host "   código de saída: $codigo"

# 2. Teste de corrida dez vezes --------------------------------------------------
Write-Host "== 2/3 Teste de corrida, dez execuções independentes"
$linhas = @()
for ($i = 1; $i -le 10; $i++) {
    $t0 = Get-Date
    $out = & .\gradlew.bat :app:testDebugUnitTest --tests "*CircularImuBufferConcurrencyTest" --rerun-tasks --console=plain 2>&1 | Out-String
    $cod = $LASTEXITCODE
    $dur = ((Get-Date) - $t0).TotalSeconds
    $testes = "?"; $falhas = "?"; $tempo = "?"
    if (Test-Path $xmlCorrida) {
        $s = ([xml](Get-Content $xmlCorrida -Raw)).testsuite
        $testes = $s.tests; $falhas = [int]$s.failures + [int]$s.errors; $tempo = $s.time
    }
    $situacao = if ($cod -eq 0 -and "$falhas" -eq "0") { "PASSOU" } else { "FALHOU" }
    $linhas += "| $i | $($t0.ToString('dd/MM/yyyy HH:mm:ss')) | $testes | $falhas | $tempo | $([math]::Round($dur,1)) | $cod | $situacao |"
    [System.IO.File]::WriteAllText((Join-Path $saida "corrida_execucao_$i.txt"), $out, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "   execução $i : $situacao"
}
$md = "# Teste de corrida — dez execuções, uma linha por execução`n`n"
$md += "- Comando (repetido 10x): ``.\gradlew.bat :app:testDebugUnitTest --tests `"*CircularImuBufferConcurrencyTest`" --rerun-tasks```n"
$md += "- Cada execução roda as 10 rodadas parametrizadas (produtor, consumidor e observador concorrentes, 10 000 leituras em buffer de 64)."
$md += " Commit ``$commit`` ($estado), $maquina.`n`n"
$md += "| Execução | Início | Casos | Falhas | Tempo JUnit (s) | Tempo total (s) | Código | Resultado |`n|---|---|---|---|---|---|---|---|`n"
$md += ($linhas -join "`n") + "`n"
Escrever "corrida_10_execucoes.md" $md

# 3. Evidências a partir dos dados reais -----------------------------------------
Write-Host "== 3/3 Evidências dos registros físicos (docs\evidencias\dados)"
& .\gradlew.bat :app:testDebugUnitTest --tests "*EvidenciasAv1Test" --rerun-tasks --console=plain -i 2>&1 |
    Select-String -Pattern "evidência gravada|SKIPPED|FAILED|assumption" | ForEach-Object { Write-Host "   $_" }

Write-Host ""
Write-Host "Pronto. Resultados em $saida"
