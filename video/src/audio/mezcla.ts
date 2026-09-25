import { interpolate } from 'remotion';
import { DURACION_TOTAL, ESCENAS, momentoGlobal, type Momento } from '../timing';

export const MUSICA = 'audio/musica/Komiku_-_03_-_Champ_de_tournesol.mp3';

export type Sonido = 'habi_meeh' | 'habi_happy' | 'habi_cheer' | 'habi_pop' | 'log_tick';

export const DURACION_SONIDO: Record<Sonido, number> = { habi_meeh: 19, habi_happy: 30, habi_cheer: 17, habi_pop: 3, log_tick: 2 };

export const MAULLIDOS: readonly Sonido[] = ['habi_meeh', 'habi_happy', 'habi_cheer'];

export const CUES: readonly { momento: Momento; sonido: Sonido; volumen: number }[] = [
  { momento: 'saludo', sonido: 'habi_happy', volumen: 1 },
  { momento: 'toqueRegistro', sonido: 'log_tick', volumen: 0.9 },
  { momento: 'tickWidget', sonido: 'log_tick', volumen: 0.9 },
  { momento: 'tickNotificacion', sonido: 'log_tick', volumen: 0.9 },
  { momento: 'tickRepaso', sonido: 'log_tick', volumen: 0.9 },
  { momento: 'celebracion', sonido: 'habi_cheer', volumen: 1 },
  { momento: 'toqueHabi', sonido: 'habi_meeh', volumen: 1 },
  { momento: 'vestida', sonido: 'habi_pop', volumen: 1 },
  { momento: 'maullido1', sonido: 'habi_cheer', volumen: 1 },
  { momento: 'maullido2', sonido: 'habi_cheer', volumen: 1 },
];

const VOL_BASE = 0.55;
const VOL_AGACHADA = 0.22;
const VOL_OSCURA = 0.3;
const RAMPA = 4;
const CLAMP = { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' } as const;

export const volumenMusica = (frame: number): number => {
  const e8 = ESCENAS[7];
  const base = interpolate(
    frame,
    [e8.desde - 15, e8.desde, e8.desde + e8.duracion, e8.desde + e8.duracion + 15],
    [VOL_BASE, VOL_OSCURA, VOL_OSCURA, VOL_BASE],
    CLAMP,
  );
  const entrada = interpolate(frame, [0, 15], [0, 1], CLAMP);
  const salida = interpolate(frame, [DURACION_TOTAL - 60, DURACION_TOTAL - 1], [1, 0], CLAMP);
  let agachada = 1;
  for (const c of CUES) {
    if (!MAULLIDOS.includes(c.sonido)) continue;
    const inicio = momentoGlobal(c.momento);
    const fin = inicio + DURACION_SONIDO[c.sonido];
    agachada = Math.min(agachada, interpolate(frame, [inicio - RAMPA, inicio, fin, fin + RAMPA], [1, VOL_AGACHADA / VOL_BASE, VOL_AGACHADA / VOL_BASE, 1], CLAMP));
  }
  return base * entrada * salida * agachada;
};
