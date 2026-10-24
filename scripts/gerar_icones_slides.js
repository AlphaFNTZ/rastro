// Gera os ícones brancos (PNG 256 px) usados pelos slides da AV1, a partir do Lucide (react-icons).
// Uso: node scripts/gerar_icones_slides.js   → docs/presentations/icones/<nome>.png
const path = require("path");
const fs = require("fs");
const React = require("react");
const { renderToStaticMarkup } = require("react-dom/server");
const sharp = require("sharp");
const lu = require("react-icons/lu");

const ICONES = {
  satelite: ["LuSatellite"], atividade: ["LuActivity"], incerteza: ["LuRadar"],
  registro: ["LuDatabase"], camadas: ["LuLayers"], anel: ["LuRefreshCw"], cadeado: ["LuLock"],
  cpu: ["LuCpu"], relogio: ["LuTimer"], semente: ["LuDices", "LuDice5"], ruido: ["LuAudioWaveform"],
  perda: ["LuWifiOff"], salto: ["LuZap"], celular: ["LuSmartphone"],
  ok: ["LuCircleCheck", "LuCheckCircle2", "LuCheckCircle"], alerta: ["LuTriangleAlert", "LuAlertTriangle"],
  alvo: ["LuTarget"], lupa: ["LuSearch"], bateria: ["LuBatteryMedium"], mapa: ["LuMapPin"],
  pergunta: ["LuCircleHelp", "LuHelpCircle"], foguete: ["LuRocket"], escudo: ["LuShieldCheck"],
  grafico: ["LuChartLine", "LuLineChart"], regua: ["LuRuler"], play: ["LuPlay"], chave: ["LuKeyRound", "LuKey"],
  rede: ["LuNetwork"], cerebro: ["LuBrain"], olho: ["LuEyeOff"], fluxo: ["LuWorkflow"], teste: ["LuFlaskConical"],
  formula: ["LuSigma"], tempo: ["LuClock"], cpuoff: ["LuGauge"],
};
const destino = path.join(__dirname, "..", "docs", "presentations", "icones");
fs.mkdirSync(destino, { recursive: true });
(async () => {
  for (const [nome, candidatos] of Object.entries(ICONES)) {
    const comp = candidatos.map((c) => lu[c]).find(Boolean);
    if (!comp) { console.error("sem ícone:", nome, candidatos.join("/")); continue; }
    const svg = renderToStaticMarkup(React.createElement(comp, { color: "#FFFFFF", size: 256, strokeWidth: 2 }));
    await sharp(Buffer.from(svg)).resize(256, 256).png().toFile(path.join(destino, `${nome}.png`));
  }
  console.log("ícones em", destino, fs.readdirSync(destino).length);
})();
