import { Easing, interpolate, measureSpring, spring, type SpringConfig } from 'remotion';
import { framesAMs, msAFrames } from '../timing';
import type { Cara, HabiPose } from './Habi';
import { MUELLES, agendaParpadeos, parpado, respiracion, type Animo } from './habiMotion';

export type Canales = { squash: number; salto: number; inclinacion: number; desplazY: number };
export const CANALES_REPOSO: Canales = { squash: 0, salto: 0, inclinacion: 0, desplazY: 0 };

export type Gesto =
  | { tipo: 'enter' }
  | { tipo: 'react-log' }
  | { tipo: 'celebrate' }
  | { tipo: 'tap'; x: number; y: number; salta: boolean }
  | { tipo: 'swap-face'; cara: Cara }
  | { tipo: 'settle' };

export const MIRADAS = {
  camara: { x: 0, y: 0 },
  derecha: { x: 1, y: 0.1 },
  izquierda: { x: -1, y: 0.1 },
  arriba: { x: 0, y: -1 },
  abajo: { x: 0, y: 1 },
  dedo: { x: 0.6, y: -0.6 },
} as const;
export type NombreMirada = keyof typeof MIRADAS;

export type EventoHabi = { at: number; gesto?: Gesto; mirada?: NombreMirada; animo?: Animo };
export type LineaHabi = { semilla: string; caraInicial?: Cara; animoInicial?: Animo; eventos: readonly EventoHabi[] };

export const ENTRADA_DESPLAZ = 1.2;
export const INCLINACION_MAX = 10;
export const INCLINACION_MIRADA = 3;
const DURACION_AGENDA_MS = 120_000;

const LOS = Easing.bezier(0, 0, 0.2, 1);
const FOLI = Easing.bezier(0.4, 0, 1, 1);
const FOSI = Easing.bezier(0.4, 0, 0.2, 1);

const tiempos = (fps: number) => ({
  presion: msAFrames(70, fps),
  pulso: msAFrames(100, fps),
  sube: msAFrames(150, fps),
  baja: msAFrames(140, fps),
  registro: msAFrames(320, fps),
  cara: msAFrames(300, fps),
  mirada: msAFrames(280, fps),
  cuerpoTrasOjos: msAFrames(100, fps),
});

const tramo = (f: number, f0: number, f1: number, a: number, b: number, easing: (t: number) => number) =>
  interpolate(f, [f0, f1], [a, b], { extrapolateLeft: 'clamp', extrapolateRight: 'clamp', easing });

const muelle = (f: number, fps: number, config: SpringConfig, duracion?: number) =>
  spring({ frame: Math.max(0, f), fps, config, ...(duracion === undefined ? {} : { durationInFrames: duracion }) });

const suelta = (desde: number, f: number, fps: number, config: SpringConfig) => desde * (1 - muelle(f, fps, config));

const asienta = (fps: number, config: SpringConfig) => measureSpring({ fps, config, threshold: 0.0002 });

export const canalesGesto = (g: Gesto, f: number, fps: number): Canales => {
  const T = tiempos(fps);
  switch (g.tipo) {
    case 'enter': {
      const s = muelle(f, fps, MUELLES.asentarse);
      return { ...CANALES_REPOSO, desplazY: (1 - s) * ENTRADA_DESPLAZ, squash: -0.4 * (1 - s) };
    }
    case 'react-log': {
      const p = T.pulso;
      const cima = 2 * T.pulso;
      const salto = f < p ? 0 : f < cima ? tramo(f, p, cima, 0, 0.35, LOS) : tramo(f, cima, T.registro, 0.35, 0, FOLI);
      const squash = f < p ? tramo(f, 0, p, 0, 0.3, FOSI) : suelta(0.3, f - p, fps, MUELLES.gelatina);
      const inclinacion = f < p ? tramo(f, 0, p, 0, -4, FOSI) : suelta(-4, f - p, fps, MUELLES.gelatina);
      return { ...CANALES_REPOSO, salto, squash, inclinacion };
    }
    case 'celebrate': {
      const a = T.sube;
      const b = T.sube + T.baja;
      if (f < a) return { ...CANALES_REPOSO, salto: tramo(f, 0, a, 0, 1, LOS), squash: tramo(f, 0, a, 0, -0.45, LOS) };
      if (f < b) return { ...CANALES_REPOSO, salto: tramo(f, a, b, 1, 0, FOLI), squash: tramo(f, a, b, -0.45, 0.35, FOLI) };
      return { ...CANALES_REPOSO, squash: suelta(0.35, f - b, fps, MUELLES.gelatina) };
    }
    case 'tap': {
      const presion = 0.4 + 0.35 * (1 - g.y);
      const giro = -(g.x - 0.5) * 2 * INCLINACION_MAX;
      const p = T.presion;
      if (f < p) return { ...CANALES_REPOSO, squash: tramo(f, 0, p, 0, presion, FOSI), inclinacion: tramo(f, 0, p, 0, giro, FOSI) };
      if (!g.salta) {
        return {
          ...CANALES_REPOSO,
          squash: suelta(presion, f - p, fps, MUELLES.gelatina),
          inclinacion: suelta(giro, f - p, fps, MUELLES.gelatina),
        };
      }
      const h = 0.75;
      const a = p + T.sube;
      const b = a + T.baja;
      if (f < a) return { ...CANALES_REPOSO, squash: tramo(f, p, a, presion, -0.45 * h, LOS), salto: tramo(f, p, a, 0, h, LOS), inclinacion: giro };
      if (f < b) return { ...CANALES_REPOSO, squash: tramo(f, a, b, -0.45 * h, 0.35, FOLI), salto: tramo(f, a, b, h, 0, FOLI), inclinacion: giro };
      return {
        ...CANALES_REPOSO,
        squash: suelta(0.35, f - b, fps, MUELLES.gelatina),
        inclinacion: suelta(giro, f - b, fps, MUELLES.gelatina),
      };
    }
    case 'swap-face':
      return { ...CANALES_REPOSO, squash: f < 2 ? tramo(f, 0, 2, 0, 0.12, FOSI) : suelta(0.12, f - 2, fps, MUELLES.gelatina) };
    case 'settle':
      return { ...CANALES_REPOSO, squash: f < 3 ? tramo(f, 0, 3, 0, 0.25, FOSI) : suelta(0.25, f - 3, fps, MUELLES.asentarse) };
  }
};

export const duracionGesto = (g: Gesto, fps: number): number => {
  const T = tiempos(fps);
  const gelatina = asienta(fps, MUELLES.gelatina);
  switch (g.tipo) {
    case 'enter':
      return asienta(fps, MUELLES.asentarse);
    case 'react-log':
      return Math.max(T.registro, T.pulso + gelatina);
    case 'celebrate':
      return T.sube + T.baja + gelatina;
    case 'tap':
      return g.salta ? T.presion + T.sube + T.baja + gelatina : T.presion + gelatina;
    case 'swap-face':
      return Math.max(T.cara, 2 + gelatina);
    case 'settle':
      return 3 + asienta(fps, MUELLES.asentarse);
  }
};

type Vec = { x: number; y: number };
type Transicion = { desde: Vec; hacia: Vec; at: number };

export const poseEnFrame = (frame: number, fps: number, linea: LineaHabi): HabiPose => {
  const T = tiempos(fps);
  const valor = (m: Transicion, f: number, retraso: number, config: SpringConfig, duracion?: number): Vec => {
    if (m.at === -Infinity) return m.hacia;
    const s = muelle(f - m.at - retraso, fps, config, duracion);
    return { x: m.desde.x + (m.hacia.x - m.desde.x) * s, y: m.desde.y + (m.hacia.y - m.desde.y) * s };
  };

  let animo: Animo = linea.animoInicial ?? 'normal';
  let cara: Cara = linea.caraInicial ?? 'neutra';
  let caraPrevia: Cara = cara;
  let caraAt = -Infinity;
  let ojos: Transicion = { desde: MIRADAS.camara, hacia: MIRADAS.camara, at: -Infinity };
  let cuerpo: Transicion = { ...ojos };
  let gesto: { g: Gesto; at: number } | null = null;
  const toques: number[] = [];

  const pasados = linea.eventos.filter((e) => e.at <= frame).sort((a, b) => a.at - b.at);
  for (const e of pasados) {
    if (e.animo) animo = e.animo;
    if (e.mirada) {
      const destino = MIRADAS[e.mirada];
      ojos = { desde: valor(ojos, e.at, 0, MUELLES.mirada, T.mirada), hacia: destino, at: e.at };
      cuerpo = { desde: valor(cuerpo, e.at, T.cuerpoTrasOjos, MUELLES.asentarse), hacia: destino, at: e.at };
    }
    if (e.gesto) {
      gesto = { g: e.gesto, at: e.at };
      if (e.gesto.tipo === 'swap-face') {
        caraPrevia = cara;
        cara = e.gesto.cara;
        caraAt = e.at;
      }
      if (e.gesto.tipo === 'tap') toques.push(framesAMs(e.at, fps));
    }
  }

  const mirada = valor(ojos, frame, 0, MUELLES.mirada, T.mirada);
  const giro = valor(cuerpo, frame, T.cuerpoTrasOjos, MUELLES.asentarse);
  const canales = gesto ? canalesGesto(gesto.g, frame - gesto.at, fps) : CANALES_REPOSO;
  const ms = framesAMs(frame, fps);
  const mezclaCara = caraAt === -Infinity ? 1 : Math.min(1, Math.max(0, muelle(frame - caraAt, fps, MUELLES.cara, T.cara)));

  return {
    respira: respiracion(frame, fps, animo),
    parpado: Math.max(parpado(ms, agendaParpadeos(linea.semilla, DURACION_AGENDA_MS, fps)), parpado(ms, toques)),
    miradaX: mirada.x,
    miradaY: mirada.y,
    squash: canales.squash,
    salto: canales.salto,
    inclinacion: canales.inclinacion + INCLINACION_MIRADA * giro.x,
    desplazY: canales.desplazY,
    cara,
    caraPrevia,
    mezclaCara,
  };
};
