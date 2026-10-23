#!/usr/bin/env python3
"""Gera o relatório (PDF e DOCX) e os slides (PPTX e PDF) da AV1 a partir das fontes do repositório.

Entradas:
  docs/relatorio/RELATORIO_AV1.md      texto do relatório, com marcadores
                                        <!-- incluir: caminho -->  e  <!-- figura: caminho | legenda -->
  docs/evidencias/resultados/*.md|.json|.svg   gerados por EvidenciasAv1Test / scripts/av1_evidencias.ps1
Saídas:
  docs/relatorio/RELATORIO_AV1.pdf e .docx
  docs/presentations/apresentacao_I_AV1.pptx e .pdf

Requer: pandoc, python-playwright (Chromium), python-pptx e LibreOffice (soffice) no PATH.
Nenhum número é digitado aqui: os valores dos slides vêm dos .json de resultados.

Uso: python scripts/gerar_documentos_av1.py
"""
import json
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
DOCS = RAIZ / "docs"
EVID = DOCS / "evidencias"
RES = EVID / "resultados"
REL = DOCS / "relatorio"
APRES = DOCS / "presentations"
FONTE = RAIZ / "app/src/main/res/font/plus_jakarta_sans.ttf"

VIOLETA = "#5E4AE3"
VERDE = "#0D9F6E"
VERMELHO = "#E03E3E"
ESCURO = "#0D0F14"
CLARO = "#F4F5F9"


def pendente(texto):
    return f"⟪Pendente: {texto}⟫"


def resolver_inclusoes(md: str) -> str:
    def incluir(m):
        partes = [p.strip() for p in m.group(1).split("|")]
        rel, modo = partes[0], (partes[1] if len(partes) > 1 else "")
        alvo = EVID / rel
        if not alvo.exists():
            return pendente(f"gerar `{rel}` com `scripts\\av1_evidencias.ps1` depois da coleta (docs/ENSAIO_AV1.md)")
        texto = alvo.read_text(encoding="utf-8")
        linhas = texto.splitlines()
        if linhas and linhas[0].startswith("# "):
            linhas = linhas[1:]
        saida = []
        dentro_codigo = False
        for l in linhas:
            if l.startswith("```"):
                dentro_codigo = not dentro_codigo
            if not dentro_codigo and l.startswith("#"):
                l = "###" + l.lstrip("#")  # rebaixa títulos internos
            saida.append(l)
        texto = "\n".join(saida).strip()
        # A saída bruta do Gradle é longa: no PDF fica só o resumo (o arquivo completo está no repositório).
        if rel.endswith("testes_unidade.md"):
            texto = re.sub(r"```text.*?```", "_Saída completa do Gradle em `docs/evidencias/resultados/testes_unidade.md`._",
                           texto, flags=re.S)
            if modo == "resumo":
                # No corpo do relatório: só o cabeçalho e os totais; a tabela por classe vai no apêndice.
                linhas = re.findall(r"^\|\s*(com\.[^|]+)\|\s*(\d+)\s*\|\s*(\d+)\s*\|\s*(\d+)\s*\|\s*(\d+)\s*\|", texto, flags=re.M)
                tot = [sum(int(l[i]) for l in linhas) for i in range(1, 5)]
                cab = [l for l in texto.splitlines() if l.startswith("- ")]
                texto = "\n".join(cab) + (f"\n\n**Total: {tot[0]} testes em {len(linhas)} classes, {tot[1]} falhas, {tot[2]} erros, "
                                          f"{tot[3]} ignorados.** A tabela por classe está no Apêndice B.")
        return texto

    def figura(m):
        rel, legenda = [p.strip() for p in m.group(1).split("|", 1)]
        alvo = EVID / rel
        if not alvo.exists():
            # Capturas de tela podem vir como .png ou .jpg; aceita qualquer uma com o mesmo nome.
            alternativas = [alvo.with_suffix(ext) for ext in (".png", ".jpg", ".jpeg", ".svg")]
            alvo = next((a for a in alternativas if a.exists()), alvo)
        if not alvo.exists():
            origem = "gerada por EvidenciasAv1Test" if rel.startswith("resultados/") else "salvar a captura com este nome"
            return pendente(f"figura `docs/evidencias/{rel}` ({origem})")
        return f"![{legenda}]({alvo.as_posix()})"

    md = re.sub(r"<!--\s*incluir:\s*(.+?)\s*-->", incluir, md)
    md = re.sub(r"<!--\s*figura:\s*(.+?)\s*-->", figura, md)
    return md


CSS = """
@font-face { font-family: 'Plus Jakarta Sans'; src: url('FONTE') format('truetype'); font-weight: 200 800; }
@page { size: A4; margin: 18mm 16mm 18mm 16mm; }
body { font-family: 'Plus Jakarta Sans', 'Helvetica Neue', Arial, sans-serif; font-size: 10pt; line-height: 1.42; color: #0D0F14; }
header#title-block-header { border-bottom: 3px solid #5E4AE3; margin-bottom: 14pt; padding-bottom: 8pt; }
h1.title { font-size: 21pt; margin: 0; color: #5E4AE3; }
p.subtitle { font-size: 11pt; margin: 3pt 0 0 0; color: #444; }
p.date { font-size: 9pt; color: #666; margin: 2pt 0 0 0; }
h1 { font-size: 14pt; color: #5E4AE3; margin: 16pt 0 5pt 0; break-after: avoid; }
h2 { font-size: 11.5pt; margin: 12pt 0 4pt 0; break-after: avoid; }
h3 { font-size: 10.5pt; margin: 9pt 0 3pt 0; break-after: avoid; }
p { margin: 0 0 6pt 0; text-align: justify; }
code { font-family: 'DejaVu Sans Mono', Consolas, monospace; font-size: 8.6pt; background: #F4F5F9; padding: 0 2px; border-radius: 2px; }
pre { font-size: 7.5pt; background: #F4F5F9; padding: 6pt; white-space: pre-wrap; }
table { border-collapse: collapse; width: 100%; margin: 4pt 0 10pt 0; font-size: 8pt; break-inside: auto; }
th { background: #EEEBFC; text-align: left; }
th, td { border: 1px solid #D5D8E2; padding: 2.5pt 4pt; vertical-align: top; }
tr { break-inside: avoid; }
blockquote { margin: 4pt 0 8pt 0; padding: 4pt 8pt; border-left: 3px solid #5E4AE3; background: #F7F6FE; color: #333; }
blockquote p { margin: 0; }
figure { margin: 6pt 0 10pt 0; text-align: center; break-inside: avoid; }
figure img { max-width: 72%; max-height: 125mm; }
.lado { display: flex; gap: 10pt; align-items: flex-start; break-inside: avoid; }
.lado figure { flex: 1; margin: 4pt 0; }
.lado figure img { max-width: 100%; max-height: 100mm; }
figcaption { font-size: 8.5pt; color: #444; margin-top: 3pt; }
.formula { text-align: center; font-weight: 600; font-size: 10.5pt; margin: 4pt 0 8pt 0; }
.formula p { text-align: center; margin: 0; }
.pendente { background: #FFF1C2; color: #6B4E00; padding: 0 2px; border-radius: 2px; }
ul { margin: 0 0 6pt 0; padding-left: 16pt; } li { margin-bottom: 2pt; }
"""


def gerar_relatorio():
    md = resolver_inclusoes((REL / "RELATORIO_AV1.md").read_text(encoding="utf-8"))
    md = re.sub(r"<!--.*?-->", "", md, flags=re.S)
    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        (tmp / "rel.md").write_text(md, encoding="utf-8")
        (tmp / "estilo.css").write_text(CSS.replace("FONTE", FONTE.as_uri()), encoding="utf-8")
        html = tmp / "rel.html"
        subprocess.run(["pandoc", str(tmp / "rel.md"), "-f", "markdown+pipe_tables+fenced_divs", "-t", "html5", "-s",
                        "--css", str(tmp / "estilo.css"), "--embed-resources", "--metadata", "lang=pt-BR",
                        "-o", str(html)], check=True)
        texto = html.read_text(encoding="utf-8")
        texto = re.sub(r"⟪([^⟫]*)⟫", r'<span class="pendente">⟪\1⟫</span>', texto)
        html.write_text(texto, encoding="utf-8")
        from playwright.sync_api import sync_playwright
        with sync_playwright() as p:
            nav = p.chromium.launch()
            pg = nav.new_page()
            pg.goto(html.as_uri())
            pg.pdf(path=str(REL / "RELATORIO_AV1.pdf"), format="A4", print_background=True,
                   display_header_footer=True, header_template="<span></span>",
                   footer_template='<div style="font-size:7pt;width:100%;text-align:center;color:#888;">'
                                   'Rastro · AV1 · <span class="pageNumber"></span>/<span class="totalPages"></span></div>',
                   margin={"top": "16mm", "bottom": "16mm", "left": "15mm", "right": "15mm"})
            nav.close()
        subprocess.run(["pandoc", str(tmp / "rel.md"), "-f", "markdown+pipe_tables", "-o", str(REL / "RELATORIO_AV1.docx"),
                        "--resource-path", str(EVID)], check=True)
    print("relatório:", REL / "RELATORIO_AV1.pdf")


def ler_json(nome):
    alvo = RES / nome
    if not alvo.exists():
        return None
    return json.loads(alvo.read_text(encoding="utf-8"))


def gerar_slides():
    from pptx import Presentation
    from pptx.dml.color import RGBColor
    from pptx.enum.shapes import MSO_SHAPE
    from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
    from pptx.util import Emu, Pt

    def rgb(h):
        return RGBColor.from_string(h.lstrip("#"))

    prs = Presentation()
    prs.slide_width, prs.slide_height = Emu(12192000), Emu(6858000)  # 16:9
    W, H = prs.slide_width, prs.slide_height
    vazio = prs.slide_layouts[6]
    FONTE_NOME = "Plus Jakarta Sans"
    cm = lambda v: Emu(int(v * 360000))

    def caixa(sl, x, y, w, h, texto="", tam=16, cor=ESCURO, negrito=False, alinh=PP_ALIGN.LEFT, fundo=None, mono=False):
        if fundo:
            fig = sl.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, x, y, w, h)
            fig.adjustments[0] = 0.06
            fig.fill.solid(); fig.fill.fore_color.rgb = rgb(fundo); fig.line.fill.background()
            fig.shadow.inherit = False
            tf = fig.text_frame
        else:
            tf = sl.shapes.add_textbox(x, y, w, h).text_frame
        tf.word_wrap = True
        tf.margin_left = tf.margin_right = cm(0.25); tf.margin_top = tf.margin_bottom = cm(0.15)
        tf.vertical_anchor = MSO_ANCHOR.TOP
        linhas = texto if isinstance(texto, list) else [texto]
        for i, l in enumerate(linhas):
            par = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
            par.alignment = alinh
            partes = l if isinstance(l, list) else [(l, negrito)]
            for t, b in partes:
                r = par.add_run(); r.text = t
                r.font.size = Pt(tam); r.font.bold = b; r.font.color.rgb = rgb(cor)
                r.font.name = "Consolas" if mono else FONTE_NOME
            par.space_after = Pt(4)
        return tf

    def base(titulo, bloco, numero):
        sl = prs.slides.add_slide(vazio)
        faixa = sl.shapes.add_shape(MSO_SHAPE.RECTANGLE, 0, 0, W, cm(0.35)); faixa.fill.solid()
        faixa.fill.fore_color.rgb = rgb(VIOLETA); faixa.line.fill.background(); faixa.shadow.inherit = False
        caixa(sl, cm(1.2), cm(0.8), cm(25), cm(1.6), titulo, 28, ESCURO, True)
        caixa(sl, cm(1.2), cm(17.6), cm(20), cm(0.8), f"{bloco}", 11, "#6B6F80")
        caixa(sl, cm(30.5), cm(17.6), cm(2.6), cm(0.8), f"{numero}/11", 11, "#6B6F80", alinh=PP_ALIGN.RIGHT)
        return sl

    def f(v, casas=1, sufixo=""):
        return "—" if v is None else f"{v:.{casas}f}{sufixo}".replace(".", ",")

    est, aq, rec = ler_json("estatico.json"), ler_json("aquisicao.json"), ler_json("reconciliacao.json")
    aq_antes = ler_json("aquisicao_antes_d23.json")
    P = "⟪após a coleta⟫"

    # 1 — título
    sl = prs.slides.add_slide(vazio)
    fundo = sl.shapes.add_shape(MSO_SHAPE.RECTANGLE, 0, 0, W, H); fundo.fill.solid(); fundo.fill.fore_color.rgb = rgb(VIOLETA)
    fundo.line.fill.background(); fundo.shadow.inherit = False
    logo = RAIZ / "rastro-logo-kit/logo/rastro-simbolo-branco-1024.png"
    if logo.exists():
        sl.shapes.add_picture(str(logo), cm(1.5), cm(1.5), height=cm(3))
    caixa(sl, cm(1.5), cm(6.2), cm(30), cm(2.6), "Rastro — sente, corrige e detecta", 40, "#FFFFFF", True)
    caixa(sl, cm(1.5), cm(8.9), cm(30), cm(1.4), "Apresentação I · AV1 · GAT108 Automação Avançada · UFLA", 18, "#E9E6FF")
    caixa(sl, cm(1.5), cm(12.6), cm(30), cm(3), ["Juliano Cesar Ferreira Ramos", "Andre Bueno Ocanha", "João Paulo Silva"], 18, "#FFFFFF")

    # 2 — problema e grandeza
    sl = base("O que a camada 1 mede", "Fundamentação · ⟪quem apresenta⟫", 2)
    caixa(sl, cm(1.2), cm(3), cm(15), cm(12), [
        [("Domínio: ", True), ("rede mesh entre Androids em áreas sem cobertura. Para a AV1, o nó precisa ", False),
         ("sentir, corrigir e detectar", True), (" sem enviar nada para fora.", False)],
        "",
        [("Fixo GNSS a cada 1 s: ", True), ("posição, velocidade (Doppler) e rumo, cada um com a acurácia de 68% que o receptor informa.", False)],
        [("Acelerômetro a 50 Hz: ", True), ("aquisição contínua em serviço de primeiro plano.", False)],
        [("Fonte da incerteza: ", True), ("multipercurso, geometria dos satélites, céu obstruído.", False)],
    ], 19)
    caixa(sl, cm(17.5), cm(3), cm(15), cm(12), [
        [("Fixo (RegistroFix)", True)],
        "tempo monotônico (ns) · UTC",
        "latitude, longitude",
        "r68 horizontal (m)",
        "velocidade s (m/s) ± σs",
        "rumo θ (°) ± σθ",
        "provedor (gps / network)",
        "",
        [("Campo ausente fica vazio: nenhuma incerteza é inventada.", True)],
    ], 18, fundo=CLARO)

    # 3 — estruturas
    sl = base("Estruturas de capacidade fixa", "Fundamentação · ⟪quem apresenta⟫", 3)
    caixa(sl, cm(1.2), cm(3), cm(31), cm(6), [
        [("Trajeto", True), (" (imutável): tamanho O(1) · média e recorte temporal O(n), n ≤ capacidade · inserção copia a janela O(n)", False)],
        [("CircularImuBuffer", True), (" (anel de 500 ≈ 10 s): inserção O(1) · descarta o mais antigo e conta · snapshot/drenar O(n) devolvem CÓPIA", False)],
        "",
        [("Por que capacidade fixa? ", True), ("o nó roda por tempo ilimitado; memória constante não depende do tempo de operação.", False)],
        [("Por que cópia? ", True), ("sem ela o consumidor itera o vetor que o produtor sobrescreve → leitura fora de ordem intermitente.", False)],
        [("O que poderia ser melhor: ", True), ("a inserção do Trajeto (O(n)); um anel mutável protegido a deixa O(1).", False)],
    ], 21)

    # 4 — incerteza
    sl = base("Incerteza vem do sensor", "Fundamentação · ⟪quem apresenta⟫", 4)
    caixa(sl, cm(1.2), cm(3), cm(16), cm(13), [
        [("Posição: ", True), ("o Android dá um raio r68 (68%), não σ.", False)],
        "P(|e| ≤ r68) = 1 − exp(−r68²/2σ²) = 0,68",
        [("σ² = r68² / (−2 ln 0,32) ≈ r68² / 2,279", True)],
        "σ² = variância por eixo (m²); r68 = raio informado (m)",
        "",
        [("Velocidade: ", True), ("vE = s·sen θ, vN = s·cos θ;  Σv = J·diag(σs², σθ²)·Jᵀ", False)],
        "J = jacobiano de (vE, vN) em relação a (s, θ); σθ em rad",
        "Parado (sem rumo): média 0, variância σs² + s²/2",
    ], 18)
    svg = RES / "dispersao_estatica.svg"
    png = None
    if svg.exists():
        png = Path(tempfile.mkdtemp()) / "dispersao.png"
        subprocess.run(["soffice", "--headless", "--convert-to", "png", "--outdir", str(png.parent), str(svg)],
                       check=False, capture_output=True)
        png = png.parent / "dispersao_estatica.png"
    if png and png.exists():
        sl.shapes.add_picture(str(png), cm(18.5), cm(2.8), height=cm(13.5))
    else:
        caixa(sl, cm(18.5), cm(3), cm(14), cm(9), [
            [("Ensaio estático (4.5)", True)],
            f"razão de otimismo = σ medido / σ declarado = {f(est and est['razao_otimismo'], 2) if est else P}",
            f"fixos dentro do próprio r68: {f(est and est['fracao_dentro_r68'] * 100, 0, '%') if est else P} (esperado 68%)",
            "",
            "> 1: o sensor promete mais do que entrega → Σ pequena demais → falsos alarmes.",
        ], 15, fundo=CLARO)

    # 5 — reconciliação
    sl = base("Reconciliação sob a restrição cinemática", "Fundamentação · ⟪quem apresenta⟫", 5)
    caixa(sl, cm(1.2), cm(3), cm(31), cm(14), [
        [("pₐ(k+1) − pₐ(k) − ½(vₐ(k) + vₐ(k+1))·Δtₖ = 0", True), ("    a ∈ {leste, norte}", False)],
        "p = posição medida (m) · v = componente da velocidade (m/s) · Δt = intervalo entre fixos (s, relógio monotônico)",
        [("Linear nas medidas (Δt é conhecido): ", True), ("A·y = 0, A com 2(N−1) linhas e 4N colunas, N = 10 fixos", False)],
        "",
        [("ŷ = y − Σ·Aᵀ·(A·Σ·Aᵀ)⁻¹·A·y", True)],
        "y = medidas · ŷ = reconciliadas · Σ = covariância do sensor · r = A·y = resíduo antes",
        [("A correção é proporcional a Σ: a medida mais incerta é a que mais se move.", True)],
        "",
        [("Descartadas: ", True), ("mínimos quadrados sem peso (puxa igual a medida boa) · Kalman (exige Q arbitrado) · |Δp| = s·Δt (não linear, perde a direção)", False)],
    ], 20)

    # 6 — erro grosseiro
    sl = base("Erro grosseiro: critério quantitativo", "Fundamentação · ⟪quem apresenta⟫", 6)
    caixa(sl, cm(1.2), cm(3), cm(19), cm(14), [
        [("zⱼ = |aⱼ| / √Wⱼⱼ", True), ("   a = y − ŷ (ajuste) · W = Σ·Aᵀ·V⁻¹·A·Σ", False)],
        "Sem erro grosseiro, zⱼ ~ N(0,1).",
        [("Corte de Šidák: ", True), ("40 medidas por janela; 1,96 em cada daria ~87% de falso alarme.", False)],
        "β = 1 − (1 − α)^(1/n), z_crítico = Φ⁻¹(1 − β/2) ≈ 3,2  (α = 5%, n = 40)",
        "Passou do corte → retira o fixo, reconcilia de novo (até 3).",
        "",
        [("Limite: ", True), ("nas pontas da janela o erro é detectado, não localizado → janela deslizante.", False)],
    ], 18)
    caixa(sl, cm(21.2), cm(3), cm(11.6), cm(9), [
        [("Sem o detector, piora (4.7)", True)],
        "Salto de 30 m no fixo central:",
        f"vizinhos SEM detector: {f(rec and rec['demo_rms_sem_detector_m'], 2, ' m') if rec else P}",
        f"vizinhos COM detector: {f(rec and rec['demo_rms_com_detector_m'], 2, ' m') if rec else P}",
        "(distância RMS à reconciliação da janela limpa)",
    ], 17, fundo=CLARO)

    # 7 — concorrência e aquisição
    sl = base("Região crítica e aquisição periódica", "Fundamentação · ⟪quem apresenta⟫", 7)
    caixa(sl, cm(1.2), cm(3), cm(16.5), cm(14), [
        [("rastro-imu-50hz", True), (" produtor, 20 ms", False)],
        [("rastro-consumidor-imu", True), (" drena a cada 1 s", False)],
        [("rastro-registro-1hz", True), (" log de aquisição", False)],
        [("principal", True), (" fixo GNSS a cada 1 s", False)],
        "",
        [("Região crítica: ", True), ("índices, slots e contadores do CircularImuBuffer mudam juntos → um monitor (synchronized).", False)],
        [("Descartados: ", True), ("ConcurrentLinkedQueue (sem limite nem descarte atômico) · ReentrantLock (não precisamos de tryLock/condições).", False)],
    ], 18)
    caixa(sl, cm(18.5), cm(3), cm(14.3), cm(13), [
        [("10 execuções do teste de corrida", True)],
        "produtor + consumidor + observador, 10 000 leituras, buffer de 64",
        "Prova: nenhuma intercalação dessas execuções corrompeu o estado.",
        "Não prova: ausência de corrida — isso vem do código (todo acesso no monitor).",
        "",
        [("Serviço em primeiro plano + PARTIAL_WAKE_LOCK", True)],
        "sem wake lock a CPU dorme e o acelerômetro (não-wakeup) para.",
        "Android = CFS, sem tempo real: período solicitado, Cᵢ medido na AV2.",
    ], 16, fundo=CLARO)

    # 8 — ensaio reprodutível
    sl = base("Ensaio reprodutível e injeção de falhas", "Fundamentação · ⟪quem apresenta⟫", 8)
    caixa(sl, cm(1.2), cm(3), cm(31), cm(14), [
        [("Semente fixa 20261020", True), (" — java.util.Random tem algoritmo especificado: mesmo resultado em qualquer JVM/Android.", False)],
        "Um gerador por (semente, rótulo): a ordem dos ensaios não muda os números.",
        "",
        [("Ruído: ", True), ("gaussiano extra, acurácia declarada inalterada (sensor otimista)", False)],
        [("Perda: ", True), ("fixos retirados com probabilidade p (Δt cresce)", False)],
        [("Salto: ", True), ("um fixo deslocado de J metros em direção sorteada (o erro grosseiro)", False)],
        "",
        [("Onde a detecção começa a falhar: ", True),
         (f"menor salto com ≥ 90% de detecção = {f(rec and rec['salto_minimo_90pct_m'], 0, ' m') if rec and rec.get('salto_minimo_90pct_m') is not None else P}", False)],
        f"Falso alarme sem salto: {f(rec['falso_alarme_controle'] * 100, 0, '%') if rec and rec.get('falso_alarme_controle') is not None else P}",
    ], 20)

    # 9 — MVP
    sl = base("MVP ao vivo — o caminho do dado", "MVP ao vivo · ⟪quem apresenta⟫ · 9 min", 9)
    passos = [
        ("1", "Aparelho bloqueado", "notificação na tela de bloqueio: fixos GNSS, IMU e tempo subindo (ativado 30+ min antes; \"Permitir o tempo todo\", economia de energia desligada)"),
        ("2", "Ensaio AV1", "toque longo no status → bloco de aquisição ao vivo"),
        ("3", "Reprodução ×10", "registro da caminhada, salto de 30 m, semente 20261020"),
        ("4", "Resíduo na tela", "RMS antes → depois (≈ 10⁻¹⁵ m), γ contra χ² 95%"),
        ("5", "Erro grosseiro", "\"ERRO GROSSEIRO no fixo N ← o salto injetado\""),
        ("6", "Falha de propósito", "salto abaixo do mínimo detectável: passa sem ser detectado"),
    ]
    for i, (n, t, d) in enumerate(passos):
        y = cm(3 + i * 2.3)
        caixa(sl, cm(1.2), y, cm(1.6), cm(1.8), n, 20, "#FFFFFF", True, PP_ALIGN.CENTER, fundo=VIOLETA)
        caixa(sl, cm(3.2), y, cm(29), cm(1.9), [[(t + "  ", True), (d, False)]], 19)

    # 10 — resultados
    sl = base("Resultados com condição de medição", "Resultados · ⟪quem apresenta⟫ · 5 min", 10)
    linhas = [
        ("Testes de unidade", "gradlew test", "ver testes_unidade.md" if (RES / "testes_unidade.md").exists() else P),
        ("Teste de corrida", "10 execuções independentes", "ver corrida_10_execucoes.md" if (RES / "corrida_10_execucoes.md").exists() else P),
        ("Aquisição bloqueada", "maior trecho contínuo", f"{f(aq['bloqueado_min'], 1, ' min')} · IMU {f(aq['imu_hz_medio'], 1, ' Hz')} · {aq['imu_descartadas']} leituras perdidas" if aq and 'imu_descartadas' in aq else P),
        ("GPS com a tela apagada", "mesmo trecho; antes da D-23 entre parênteses",
         f"{f(aq['cobertura_gps'] * 100, 0, '%')} do tempo" + (f" ({f(aq_antes['cobertura_gps'] * 100, 0, '%')})" if aq_antes else "") +
         f" · sem GPS, rede a cada ≤ {f(aq['maior_intervalo_qualquer_fixo_s'], 0, ' s')}" if aq and aq.get('cobertura_gps') is not None else P),
        ("Consumo", "bateria durante o trecho", f(aq['consumo_pct_h'], 1, ' %/h') if aq and aq.get('consumo_pct_h') is not None else P),
        ("Razão de otimismo", f"parado; {f(est['fracao_repetidos'] * 100, 0, '%')} dos fixos retidos" if est and est.get('fracao_repetidos') is not None else "aparelho parado",
         f"{f(est['razao_otimismo'], 2)} (não mede o ruído)" if est else P),
        ("Resíduo RMS", f"{rec['janelas']} janelas reais da caminhada" if rec else "≥ 3 janelas reais", f"{f(rec['rms_antes_medio_m'], 2, ' m')} → {rec['rms_depois_max_m']:.0e} m" if rec else P),
        ("Salto mínimo detectável", "≥ 90% de detecção", f(rec['salto_minimo_90pct_m'], 0, ' m') if rec and rec.get('salto_minimo_90pct_m') is not None else P),
    ]
    tab = sl.shapes.add_table(len(linhas) + 1, 3, cm(1.2), cm(3), cm(31.5), cm(12)).table
    for j, cab in enumerate(["Grandeza", "Condição", "Valor"]):
        c = tab.cell(0, j); c.text = cab
    for i, linha in enumerate(linhas, start=1):
        for j, v in enumerate(linha):
            tab.cell(i, j).text = v
    for i in range(len(linhas) + 1):
        for j in range(3):
            cel = tab.cell(i, j)
            cel.fill.solid(); cel.fill.fore_color.rgb = rgb(VIOLETA if i == 0 else ("#FFFFFF" if i % 2 else CLARO))
            for par in cel.text_frame.paragraphs:
                for r in par.runs:
                    r.font.size = Pt(15); r.font.name = FONTE_NOME; r.font.bold = (i == 0)
                    r.font.color.rgb = rgb("#FFFFFF" if i == 0 else ESCURO)
    caixa(sl, cm(1.2), cm(15.4), cm(31), cm(1.5), "Aparelho: Samsung SM-S926B · coletas: 22/10/2026, campus da UFLA (sessão bloqueada de 21h12 fora do campus, coordenadas deslocadas) · scripts\\av1_evidencias.ps1 · semente 20261020", 12, "#6B6F80")

    # 11 — limitações
    sl = base("Limitações e próximo passo", "Resultados · ⟪quem apresenta⟫", 11)
    caixa(sl, cm(1.2), cm(3), cm(15.5), cm(14), [
        [("O que não sabemos ainda", True)],
        "Sem verdade de campo: a referência é a janela limpa reconciliada.",
        "Erro de GNSS é correlacionado no tempo: γ não é exatamente χ².",
        (f"Sem localização em segundo plano o GPS parava ~3 min após apagar a tela; com ela, {f(aq['cobertura_gps'] * 100, 0, '%')} do tempo "
         f"e uma pausa de {f(aq['maior_intervalo_fixos_s'] / 60, 1, ' min')} sem causa conhecida.") if aq and aq.get('cobertura_gps') is not None else P,
        "Parado, o receptor retém a posição: o ensaio estático não mede o ruído.",
        "Registro em claro até a cifragem da AV2.",
    ], 17)
    caixa(sl, cm(17.5), cm(3), cm(15.3), cm(14), [
        [("AV2", True)],
        "Pedir a localização em segundo plano no app; consumidor IMU com taxa fixa (D-22)",
        "Cᵢ medido e escalonabilidade das 4 tarefas periódicas",
        "Aprendizado online dos encontros de rede",
        "Agente com RuleEngine e modelo removível",
        "AES-GCM com chave no Keystore e frota com deriva de relógio",
    ], 19, fundo=CLARO)

    APRES.mkdir(parents=True, exist_ok=True)
    pptx = APRES / "apresentacao_I_AV1.pptx"
    prs.save(str(pptx))
    subprocess.run(["soffice", "--headless", "--convert-to", "pdf", "--outdir", str(APRES), str(pptx)], check=True,
                   capture_output=True)
    print("slides:", pptx, "e", pptx.with_suffix(".pdf"))


if __name__ == "__main__":
    alvo = sys.argv[1] if len(sys.argv) > 1 else "tudo"
    if alvo in ("tudo", "relatorio"):
        gerar_relatorio()
    if alvo in ("tudo", "slides"):
        gerar_slides()
