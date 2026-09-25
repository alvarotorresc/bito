import type { Lang } from '../copy';

export const PANTALLAS = [
  'hoy', 'detalle', 'stats', 'records', 'logros', 'habi', 'tienda',
  'repaso', 'ajustes', 'tareas', 'foco', 'widget', 'notificacion',
] as const;
export type Pantalla = (typeof PANTALLAS)[number];

export const rutaShot = (lang: Lang, p: Pantalla) => `shots/${lang}/${p}.png`;

export const resolverShot = (disponibles: readonly string[], lang: Lang, p: Pantalla): string | null =>
  disponibles.includes(rutaShot(lang, p)) ? rutaShot(lang, p) : null;

export const shotsQueFaltan = (disponibles: readonly string[]): string[] =>
  (['es', 'en'] as const).flatMap((lang) => PANTALLAS.map((p) => rutaShot(lang, p))).filter((r) => !disponibles.includes(r));

export type Punto = { x: number; y: number };

export const TOQUES = {
  hoyRegistro: { x: 0.86, y: 0.31 },
  widget: { x: 0.5, y: 0.45 },
  notificacion: { x: 0.78, y: 0.2 },
  repaso: { x: 0.5, y: 0.86 },
  hoyADetalle: { x: 0.35, y: 0.31 },
  detalleAStats: { x: 0.5, y: 0.955 },
  statsALogros: { x: 0.82, y: 0.955 },
  hoyATareas: { x: 0.5, y: 0.62 },
  tareasAFoco: { x: 0.86, y: 0.22 },
} as const satisfies Record<string, Punto>;
