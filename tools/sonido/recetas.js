// Recetas de sonido de Bito, copiadas de ~/Pictures/bito-habi/direcciones/14-app/sistema/sistema.js para
// renderizarlas a archivos. Solo cambian tone() y noise(): escriben en un OfflineAudioContext (44,1 kHz,
// mono) en lugar del contexto en vivo, sin ganancia de canal ni de master (el volumen lo pone la app), y
// el ruido sale de un generador con semilla para que cada renderizado dé lo mismo.
'use strict';
const SR = 44100;
let C = null;   // contexto del sonido en curso; null en la pasada en seco
let FIN = 0;    // final más tardío de la pasada en seco, en segundos
const S = { reg: true }; // las recetas miran S.reg («Sonido al registrar»); aquí siempre encendido

let semilla = 1;
function azar(){ // mulberry32
  semilla = (semilla + 0x6D2B79F5) | 0;
  let t = Math.imul(semilla ^ (semilla >>> 15), 1 | semilla);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
}

// Cada voz (un tone() o un noise()) se renderiza en su propio contexto y se suman a mano en el orden de la
// receta: Chrome suma las entradas de un mismo nodo en un orden que cambia de una ejecución a otra, y como la
// suma en coma flotante no es asociativa los archivos no salían idénticos.
let VOCES = [];
function tone(f1, f2, dur, o = {}){
  const { type = 'sine', g = .1, at = 0, a = .008, lp = 0 } = o;
  const t = at + .005;
  if (!C){ FIN = Math.max(FIN, t + dur + .05); return; }
  VOCES.push(ctx => {
    const osc = ctx.createOscillator(), v = ctx.createGain();
    osc.type = type; osc.frequency.setValueAtTime(f1, t);
    if (f2 !== f1) osc.frequency.exponentialRampToValueAtTime(f2, t + dur*.8);
    v.gain.setValueAtTime(.0001, t); v.gain.exponentialRampToValueAtTime(g, t + a); v.gain.exponentialRampToValueAtTime(.0001, t + dur);
    osc.connect(v);
    if (lp){ const f = ctx.createBiquadFilter(); f.type = 'lowpass'; f.frequency.value = lp; v.connect(f); f.connect(ctx.destination); } else v.connect(ctx.destination);
    osc.start(t); osc.stop(t + dur + .05);
  });
}
let noiseDatos = null;
function noise(dur, o = {}){
  const { g = .05, f1 = 900, f2 = 300, a = .05, at = 0 } = o;
  const t = at + .005;
  if (!C){ FIN = Math.max(FIN, t + dur + .05); return; }
  if (!noiseDatos){ noiseDatos = new Float32Array(SR*3); let last = 0;
    for (let i = 0; i < noiseDatos.length; i++){ last = last*.92 + (azar()*2 - 1)*.08; noiseDatos[i] = last*3; } }
  const datos = noiseDatos;
  VOCES.push(ctx => {
    const buf = ctx.createBuffer(1, datos.length, SR); buf.copyToChannel(datos, 0);
    const src = ctx.createBufferSource(), f = ctx.createBiquadFilter(), v = ctx.createGain();
    src.buffer = buf; f.type = 'lowpass'; f.frequency.setValueAtTime(f1, t); f.frequency.exponentialRampToValueAtTime(f2, t + dur);
    v.gain.setValueAtTime(.0001, t); v.gain.exponentialRampToValueAtTime(g, t + a); v.gain.exponentialRampToValueAtTime(.0001, t + dur);
    src.connect(f).connect(v).connect(ctx.destination); src.start(t); src.stop(t + dur + .05);
  });
}

// ===== PEGADO de sistema.js, tal cual =====
const SND = {
  gorgoteo(){ if (!S.reg) return; [0,1,2,3].forEach(i => tone(230 + i*75, (230 + i*75)*1.9, .075, { g:.075 + i*.012, at:i*.085, a:.007 })); },   // marcar: el agua sube
  llegada(force){ if (!S.reg && !force) return; tone(1320, 1400, .12, { g:.06, a:.006 }); tone(2640, 2720, .08, { g:.012, a:.006 }); },          // la gota llega
  vaciar(){ if (!S.reg) return; tone(380, 260, .15, { g:.06, a:.01 }); },                                                                         // desmarcar
  brote(){ tone(990, 1480, .09, { g:.05, a:.008 }); },                        // algo brota: hoja, aviso, opción elegida
  hundir(){ tone(320, 210, .14, { g:.05, a:.012 }); },                         // algo se hunde y se va
  asentar(){ noise(.45, { g:.06, f1:700, f2:220, a:.07 }); },                  // tarea hecha: tierra que se asienta
  posar(){ noise(.16, { g:.045, f1:800, f2:240, a:.012 }); tone(300, 220, .12, { g:.035, a:.012, lp:900 }); },   // algo se posa en tierra blanda
  campanilla(){ tone(1568, 1568, .5, { g:.09, a:.004 }); tone(2349, 2349, .32, { g:.025, a:.004 }); tone(3920, 3920, .18, { g:.012, a:.004 }); },
  gastar(){ tone(1046, 1046, .16, { g:.05, a:.006 }); tone(784, 784, .24, { g:.05, a:.006, at:.09 }); },
  habi(){ tone(420, 660, .17, { type:'triangle', g:.08, a:.012, lp:1800, ch:'habi' }); },
  planta(){ tone(1180, 1080, .11, { g:.045, a:.008 }); },
  interruptor(on){ tone(on ? 620 : 700, on ? 760 : 560, .07, { g:.04, a:.01 }); },
  anochecer(){ noise(2.9, { g:.05, f1:1400, f2:160, a:.5, ch:'ambiente' }); tone(98, 98, 3.2, { g:.06, a:.8, ch:'ambiente' }); tone(147, 147, 3.0, { g:.025, a:1, at:.3, ch:'ambiente' }); },
  descenso(){ tone(540, 260, .42, { g:.045, a:.02, lp:1200 }); },              // bajar un nivel (entrar en una ficha, abrir un estrato)
  ascenso(){ tone(260, 540, .36, { g:.045, a:.02, lp:1200 }); },               // volver arriba
  zoom(){ tone(330, 495, .55, { type:'triangle', g:.05, a:.06, lp:1400, ch:'ambiente' }); },
  alejar(){ noise(.5, { g:.05, f1:1100, f2:420, a:.06 }); },
  acercar(){ noise(.42, { g:.045, f1:420, f2:1100, a:.05 }); },
  tic(){ tone(1760, 1760, .05, { g:.025, a:.006 }); },                         // cristal pequeñito: cada paso de un selector
  pasoMes(dir){ tone(dir < 0 ? 880 : 990, dir < 0 ? 780 : 1110, .1, { g:.04, a:.008 }); },
  ambiente(){ tone(196, 294, .9, { type:'triangle', g:.06, a:.25, lp:900, ch:'ambiente' }); },
  aviso(){ tone(660, 660, .22, { g:.06, a:.01 }); tone(990, 990, .3, { g:.035, a:.01, at:.12 }); },
  moneda(k = 1){ tone(2093, 2093, .1, { g:.03*k, a:.004 }); tone(3136, 3136, .06, { g:.008*k, a:.004 }); },
  diaCompleto(){ [784, 988, 1175, 1568].forEach((f, i) => { tone(f, f, .55, { g:.07 - i*.008, a:.005, at:i*.09 }); tone(f*2, f*2, .25, { g:.012, a:.005, at:i*.09 }); });
    tone(392, 392, .9, { type:'triangle', g:.05, a:.08, lp:900 }); noise(.7, { g:.025, f1:3000, f2:1200, a:.05, at:.3 }); },
  racha(){ noise(.55, { g:.04, f1:600, f2:1800, a:.12 }); tone(523, 659, .32, { type:'triangle', g:.06, a:.02, lp:2000, at:.12 }); tone(784, 784, .5, { g:.06, a:.006, at:.36 }); tone(1568, 1568, .3, { g:.014, a:.006, at:.36 }); },
  abrir(){ noise(.5, { g:.045, f1:420, f2:1300, a:.08 }); tone(440, 660, .5, { type:'triangle', g:.035, a:.08, lp:1600, ch:'ambiente' }); },
  cerrar(){ noise(.38, { g:.04, f1:1200, f2:420, a:.05 }); },
  lado(dir){ tone(dir > 0 ? 620 : 760, dir > 0 ? 760 : 620, .16, { g:.035, a:.01, lp:2200 }); },
  // Logro (decisión 12): brote al empezar y campanilla cuando el disco se enciende (0,6 s). Hasta tener uno propio, que se afina en Kotlin.
  logro(){ tone(990, 1480, .09, { g:.05, a:.008 }); tone(1568, 1568, .5, { g:.09, a:.004, at:.6 }); tone(2349, 2349, .32, { g:.025, a:.004, at:.6 }); tone(3920, 3920, .18, { g:.012, a:.004, at:.6 }); },
};
// ===== FIN DE LO PEGADO =====

const LISTA = [
  ['snd_gorgoteo', () => SND.gorgoteo()], ['snd_llegada', () => SND.llegada(true)], ['snd_vaciar', () => SND.vaciar()],
  ['snd_brote', () => SND.brote()], ['snd_hundir', () => SND.hundir()], ['snd_asentar', () => SND.asentar()],
  ['snd_posar', () => SND.posar()], ['snd_campanilla', () => SND.campanilla()], ['snd_gastar', () => SND.gastar()],
  ['snd_habi', () => SND.habi()], ['snd_planta', () => SND.planta()],
  ['snd_interruptor_on', () => SND.interruptor(true)], ['snd_interruptor_off', () => SND.interruptor(false)],
  ['snd_anochecer', () => SND.anochecer()], ['snd_descenso', () => SND.descenso()], ['snd_ascenso', () => SND.ascenso()],
  ['snd_zoom', () => SND.zoom()], ['snd_alejar', () => SND.alejar()], ['snd_acercar', () => SND.acercar()],
  ['snd_abrir', () => SND.abrir()], ['snd_cerrar', () => SND.cerrar()], ['snd_tic', () => SND.tic()],
  ['snd_pasomes_atras', () => SND.pasoMes(-1)], ['snd_pasomes_delante', () => SND.pasoMes(1)],
  ['snd_lado_izq', () => SND.lado(-1)], ['snd_lado_der', () => SND.lado(1)],
  ['snd_ambiente', () => SND.ambiente()], ['snd_aviso', () => SND.aviso()], ['snd_moneda', () => SND.moneda()],
  ['snd_diacompleto', () => SND.diaCompleto()], ['snd_racha', () => SND.racha()], ['snd_logro', () => SND.logro()],
];

async function renderizar(receta){
  semilla = 1; noiseDatos = null; VOCES = []; C = null; FIN = 0; receta();   // en seco: cuánto dura
  const largo = Math.ceil((FIN + .02) * SR);                                           // + 20 ms de margen
  C = true; VOCES = []; semilla = 1; noiseDatos = null; receta(); C = null;
  const suma = new Float64Array(largo);
  for (const voz of VOCES){
    const ctx = new OfflineAudioContext(1, largo, SR);
    voz(ctx);
    const d = (await ctx.startRendering()).getChannelData(0);
    for (let i = 0; i < largo; i++) suma[i] += d[i];
  }
  VOCES = [];
  return Float32Array.from(suma);
}

// Fondos (SND.fondoBuf y FONDOS de sistema.js): bucle de 4 s. La muestra cruda ya cosida (los últimos 0,25 s
// fundidos a potencia constante con el principio) pasa tres veces por el filtro y se queda el periodo del medio:
// el filtro llega en régimen a la costura y no hay salto. La ganancia de FONDOS va dentro. Desviación: el vaivén
// de la cafetería va a 0,25 Hz (una vuelta entera en 4 s) en lugar de 0,23 Hz.
const FONDOS = { lluvia:['highpass', 700, .5, .16], marron:['lowpass', 420, .7, .5], cafe:['bandpass', 520, .6, .3] };
function crudoCosido(){
  const L = SR*4, F = Math.round(SR*.25), a = new Float32Array(L + F); let l = 0; semilla = 1;
  for (let i = 0; i < a.length; i++){ const w = azar()*2 - 1; l = l*.985 + w*.06; a[i] = l*2.2 + w*.05; }
  const d = new Float32Array(L);
  for (let i = 0; i < L; i++){ if (i < F){ const x = i/F; d[i] = a[i]*Math.sin(x*Math.PI/2) + a[L + i]*Math.cos(x*Math.PI/2); } else d[i] = a[i]; }
  return d;
}
async function renderizarFondo(tipo){
  const m = FONDOS[tipo], L = SR*4, c = new OfflineAudioContext(1, L*3, SR);
  const b = c.createBuffer(1, L, SR); b.copyToChannel(crudoCosido(), 0);
  const src = c.createBufferSource(), f = c.createBiquadFilter(), g = c.createGain();
  src.buffer = b; src.loop = true; f.type = m[0]; f.frequency.value = m[1]; f.Q.value = m[2]; g.gain.value = m[3];
  src.connect(f).connect(g).connect(c.destination);
  if (tipo === 'cafe'){ const lfo = c.createOscillator(), lg = c.createGain(); lfo.frequency.value = .25; lg.gain.value = .08; lfo.connect(lg).connect(g.gain); lfo.start(0); }
  src.start(0);
  return (await c.startRendering()).getChannelData(0).slice(L, 2*L);
}

function base64(f32){
  const u8 = new Uint8Array(f32.buffer, f32.byteOffset, f32.byteLength); let s = '';
  for (let i = 0; i < u8.length; i += 0x8000) s += String.fromCharCode.apply(null, u8.subarray(i, i + 0x8000));
  return btoa(s);
}
const NOMBRES = LISTA.map(x => x[0]).concat(['snd_fondo_lluvia', 'snd_fondo_marron', 'snd_fondo_cafe']);
async function renderizarUno(nombre){
  if (nombre.startsWith('snd_fondo_')) return base64(await renderizarFondo(nombre.slice('snd_fondo_'.length)));
  return base64(await renderizar(LISTA.find(x => x[0] === nombre)[1]));
}
window.NOMBRES = NOMBRES; window.renderizarUno = renderizarUno;
