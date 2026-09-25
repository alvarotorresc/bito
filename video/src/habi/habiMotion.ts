import { Easing, random, type SpringConfig } from 'remotion';
import { framesAMs } from '../timing';

export const BREATH_SCALE = 0.022;
export const BREATH_LIFT = 0.008;
export const SQUASH_X = 0.1;
export const SQUASH_Y = 0.14;
export const SALTO_ALTURA = 8 / 150;
export const GAZE_X = 0.014;
export const GAZE_Y = 0.01;

export const dampingDesdeRatio = (ratio: number, rigidez: number, masa = 1) => 2 * ratio * Math.sqrt(rigidez * masa);

const muelle = (ratio: number, rigidez: number): SpringConfig => ({
  damping: dampingDesdeRatio(ratio, rigidez),
  stiffness: rigidez,
  mass: 1,
  overshootClamping: false,
});

export const MUELLES = {
  gelatina: muelle(0.32, 380),
  asentarse: muelle(0.6, 400),
  mirada: muelle(0.85, 300),
  cara: muelle(0.8, 260),
} as const;

export type Animo = 'radiante' | 'normal' | 'bajo';

export const PERIODO_RESPIRACION_MS: Record<Animo, number> = { radiante: 2000, normal: 2700, bajo: 3500 };

const easeInOut = Easing.bezier(0.42, 0, 0.58, 1);

export const respiracion = (frame: number, fps: number, animo: Animo): number => {
  const periodo = PERIODO_RESPIRACION_MS[animo];
  const ms = framesAMs(frame, fps) % (2 * periodo);
  const fase = ms <= periodo ? ms / periodo : 2 - ms / periodo;
  return easeInOut(fase);
};

export const PARPADO_CIERRA_MS = 60;
export const PARPADO_ABRE_MS = 110;
const ESPERA_MIN_MS = 3000;
const ESPERA_MAX_MS = 5000;
const DOBLE_UNO_DE = 4;
const HUECO_DOBLE_MS = 90;

const alineaPico = (ms: number, fps: number) =>
  framesAMs(Math.round(((ms + PARPADO_CIERRA_MS) * fps) / 1000), fps) - PARPADO_CIERRA_MS;

export const agendaParpadeos = (semilla: string, duracionMs: number, fps: number): number[] => {
  const inicios: number[] = [];
  let t = 0;
  for (let i = 0; ; i++) {
    t += ESPERA_MIN_MS + random(`${semilla}-espera-${i}`) * (ESPERA_MAX_MS - ESPERA_MIN_MS);
    if (t >= duracionMs) return inicios;
    const inicio = alineaPico(t, fps);
    inicios.push(inicio);
    t = inicio + PARPADO_CIERRA_MS + PARPADO_ABRE_MS;
    if (random(`${semilla}-doble-${i}`) < 1 / DOBLE_UNO_DE) {
      const segundo = alineaPico(t + HUECO_DOBLE_MS, fps);
      inicios.push(segundo);
      t = segundo + PARPADO_CIERRA_MS + PARPADO_ABRE_MS;
    }
  }
};

export const parpado = (ms: number, inicios: readonly number[]): number => {
  let valor = 0;
  for (const inicio of inicios) {
    const d = ms - inicio;
    if (d < 0 || d >= PARPADO_CIERRA_MS + PARPADO_ABRE_MS) continue;
    valor = Math.max(valor, d < PARPADO_CIERRA_MS ? d / PARPADO_CIERRA_MS : 1 - (d - PARPADO_CIERRA_MS) / PARPADO_ABRE_MS);
  }
  return valor;
};

const entre01 = (v: number) => Math.min(1, Math.max(0, v));

export const sombra = (p: { salto: number; squash: number; desplazY: number }) => ({
  escalaX: (1 - 0.35 * entre01(p.salto)) * (1 + SQUASH_X * Math.max(0, p.squash)),
  opacidad: (1 - 0.5 * entre01(p.salto)) * (1 - entre01(p.desplazY / 0.3)),
});
