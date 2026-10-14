#!/usr/bin/env node
// Renderiza tools/sonido/recetas.js con Chrome headless (OfflineAudioContext) y pasa cada sonido a OGG
// Vorbis mono de 44,1 kHz en res/raw (spec pieza 2, §4.4.2). Una sola normalización para todos (pico
// común a −1 dBFS): las proporciones entre sonidos se conservan y el volumen lo pone la app.
import { spawn, spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, writeFileSync, rmSync, statSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const AQUI = dirname(fileURLToPath(import.meta.url));
const SALIDA = process.env.SALIDA || resolve(AQUI, '..', '..', 'app', 'src', 'main', 'res', 'raw');
const SISTEMA = process.env.SISTEMA_JS || join(process.env.HOME, 'Pictures/bito-habi/direcciones/14-app/sistema/sistema.js');
const CHROME = process.env.CHROME || 'google-chrome';
const SR = 44100, PRESUPUESTO = 600 * 1024, PICO = Math.pow(10, -1 / 20), SILENCIO = 1e-4;

function bloqueSND(texto){
  const i = texto.indexOf('const SND = {'), j = texto.indexOf('\n};', i);
  if (i < 0 || j < 0) throw new Error('No encuentro el objeto SND');
  return texto.slice(i, j + 3).replace(/\s+/g, ' ');
}
if (bloqueSND(readFileSync(SISTEMA, 'utf8')) !== bloqueSND(readFileSync(join(AQUI, 'recetas.js'), 'utf8'))) {
  console.error('recetas.js no copia el objeto SND de sistema.js tal cual. Vuelve a pegarlo.');
  process.exit(2);
}

const tmp = mkdtempSync(join(tmpdir(), 'bito-snd-'));
const chrome = spawn(CHROME, ['--headless=new', '--remote-debugging-port=0', '--no-first-run', '--no-default-browser-check',
  `--user-data-dir=${join(tmp, 'perfil')}`, 'about:blank'], { stdio: ['ignore', 'ignore', 'pipe'] });
function salir(codigo){ try { chrome.kill(); } catch {} try { rmSync(tmp, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 }); } catch {} process.exit(codigo); }

const wsUrl = await new Promise((ok, mal) => {
  const reloj = setTimeout(() => mal(new Error('Chrome no ha arrancado en 20 s')), 20000);
  chrome.stderr.on('data', d => { const m = /ws:\/\/\S+/.exec(String(d)); if (m) { clearTimeout(reloj); ok(m[0]); } });
  chrome.on('exit', c => mal(new Error(`Chrome ha salido con ${c}`)));
}).catch(e => { console.error(e.message); salir(3); });
const paginas = await (await fetch(wsUrl.replace('ws://', 'http://').replace(/\/devtools\/.*/, '/json/list'))).json();
const ws = new WebSocket(paginas.find(p => p.type === 'page').webSocketDebuggerUrl);
await new Promise(ok => ws.addEventListener('open', ok));
let n = 0; const esperando = new Map(), eventos = [];
ws.addEventListener('message', e => {
  const m = JSON.parse(e.data);
  if (m.id && esperando.has(m.id)) { esperando.get(m.id)(m); esperando.delete(m.id); } else if (m.method) eventos.push(m.method);
});
const cdp = (method, params = {}) => new Promise(ok => { const id = ++n; esperando.set(id, ok); ws.send(JSON.stringify({ id, method, params })); });
async function evaluar(expression){
  const r = await cdp('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
  if (r.error || r.result.exceptionDetails) throw new Error(JSON.stringify(r.error || r.result.exceptionDetails));
  return r.result.result.value;
}

await cdp('Page.enable');
await cdp('Page.navigate', { url: pathToFileURL(join(AQUI, 'renderizar.html')).href });
for (let i = 0; i < 200 && !eventos.includes('Page.loadEventFired'); i++) await new Promise(ok => setTimeout(ok, 50));

const muestras = {};
try {
  for (const nombre of await evaluar('NOMBRES')) {
    const bytes = new Uint8Array(Buffer.from(await evaluar(`renderizarUno(${JSON.stringify(nombre)})`), 'base64'));
    muestras[nombre] = new Float32Array(bytes.buffer);
  }
} catch (e) { console.error(e.message); salir(3); }
ws.close();

let pico = 0;
for (const [nombre, m] of Object.entries(muestras)) {
  let p = 0; for (const x of m) p = Math.max(p, Math.abs(x));
  if (!(p > SILENCIO)) { console.error(`${nombre} sale en silencio.`); salir(4); }
  pico = Math.max(pico, p);
}
const factor = PICO / pico;

// Costura de los fondos: el salto entre la última muestra y la primera no puede ser mayor que los saltos normales.
for (const nombre of Object.keys(muestras).filter(k => k.startsWith('snd_fondo_'))) {
  const m = muestras[nombre], saltos = [];
  for (let i = 1; i < m.length; i++) saltos.push(Math.abs(m[i] - m[i - 1]));
  saltos.sort((a, b) => a - b);
  const p999 = saltos[Math.floor(saltos.length * 0.999)], costura = Math.abs(m[0] - m[m.length - 1]);
  if (m.length !== SR * 4 || costura > p999) { console.error(`${nombre}: costura ${costura.toFixed(5)} > ${p999.toFixed(5)} o largo ${m.length}.`); salir(6); }
}

function wav(m){
  const b = Buffer.alloc(44 + m.length * 2);
  b.write('RIFF', 0); b.writeUInt32LE(36 + m.length * 2, 4); b.write('WAVE', 8); b.write('fmt ', 12);
  b.writeUInt32LE(16, 16); b.writeUInt16LE(1, 20); b.writeUInt16LE(1, 22); b.writeUInt32LE(SR, 24);
  b.writeUInt32LE(SR * 2, 28); b.writeUInt16LE(2, 32); b.writeUInt16LE(16, 34); b.write('data', 36); b.writeUInt32LE(m.length * 2, 40);
  for (let i = 0; i < m.length; i++) b.writeInt16LE(Math.max(-32768, Math.min(32767, Math.round(m[i] * factor * 32767))), 44 + i * 2);
  return b;
}

let total = 0;
for (const [nombre, m] of Object.entries(muestras)) {
  const entrada = join(tmp, `${nombre}.wav`), salida = join(SALIDA, `${nombre}.ogg`);
  writeFileSync(entrada, wav(m));
  const r = spawnSync('ffmpeg', ['-hide_banner', '-loglevel', 'error', '-y', '-i', entrada, '-c:a', 'libvorbis', '-q:a', '4',
    '-ac', '1', '-ar', String(SR), '-map_metadata', '-1', '-fflags', '+bitexact', '-flags:a', '+bitexact', salida], { stdio: 'inherit' });
  if (r.status !== 0) { console.error(`ffmpeg ha fallado con ${nombre}.`); salir(5); }
  const kb = statSync(salida).size; total += kb;
  console.log(`${nombre.padEnd(22)} ${String(Math.round(m.length / SR * 1000)).padStart(5)} ms  ${(kb / 1024).toFixed(1).padStart(6)} KB`);
}
console.log(`${Object.keys(muestras).length} sonidos, ${(total / 1024).toFixed(0)} KB en total (presupuesto ${PRESUPUESTO / 1024} KB).`);
if (total > PRESUPUESTO) { console.error('Pasa del presupuesto: baja -q:a a 3 en renderizar.mjs y vuelve a ejecutar.'); salir(7); }
salir(0);
