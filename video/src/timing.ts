export const FPS = 30;
export const ANCHO = 1920;
export const ALTO = 1080;
export const DURACION_TOTAL = 2070;

const s = (segundos: number) => segundos * FPS;

export const ESCENAS = [
  { n: 1, nombre: 'apertura', desde: s(0), duracion: s(4) },
  { n: 2, nombre: 'que-es', desde: s(4), duracion: s(6) },
  { n: 3, nombre: 'registro', desde: s(10), duracion: s(4) },
  { n: 4, nombre: 'triptico', desde: s(14), duracion: s(6) },
  { n: 5, nombre: 'navegacion', desde: s(20), duracion: s(10) },
  { n: 6, nombre: 'habi', desde: s(30), duracion: s(16) },
  { n: 7, nombre: 'tareas', desde: s(46), duracion: s(6) },
  { n: 8, nombre: 'respiracion', desde: s(52), duracion: s(5) },
  { n: 9, nombre: 'promesas', desde: s(57), duracion: s(6) },
  { n: 10, nombre: 'cierre', desde: s(63), duracion: s(6) },
] as const;

export type NumEscena = (typeof ESCENAS)[number]['n'];

export const escena = (n: NumEscena) => ESCENAS[n - 1];

export const msAFrames = (ms: number, fps: number = FPS) => Math.round((ms * fps) / 1000);

export const framesAMs = (frames: number, fps: number = FPS) => (frames * 1000) / fps;

export const MOMENTOS = {
  saludo: { escena: 1, local: 20 },
  miraCamara: { escena: 1, local: 34 },
  movilEntra: { escena: 2, local: 30 },
  miraMovil: { escena: 2, local: 42 },
  rotuloSin: { escena: 2, local: 90 },
  toqueRegistro: { escena: 3, local: 45 },
  tickWidget: { escena: 4, local: 40 },
  tickNotificacion: { escena: 4, local: 90 },
  tickRepaso: { escena: 4, local: 140 },
  toqueDetalle: { escena: 5, local: 50 },
  toqueStats: { escena: 5, local: 130 },
  toqueLogros: { escena: 5, local: 210 },
  celebracion: { escena: 5, local: 228 },
  toqueHabi: { escena: 6, local: 24 },
  vozTitular: { escena: 6, local: 75 },
  caraSargento: { escena: 6, local: 135 },
  caraCheerleader: { escena: 6, local: 225 },
  caraNeutra: { escena: 6, local: 315 },
  tiendaEntra: { escena: 6, local: 405 },
  vestida: { escena: 6, local: 410 },
  toqueTareas: { escena: 7, local: 50 },
  toqueFoco: { escena: 7, local: 105 },
  toqueRespiracion: { escena: 8, local: 45 },
  asentarse: { escena: 10, local: 12 },
  maullido1: { escena: 10, local: 40 },
  maullido2: { escena: 10, local: 62 },
} as const satisfies Record<string, { escena: NumEscena; local: number }>;

export type Momento = keyof typeof MOMENTOS;

export const momentoLocal = (m: Momento): number => MOMENTOS[m].local;

export const momentoGlobal = (m: Momento): number => escena(MOMENTOS[m].escena).desde + MOMENTOS[m].local;
