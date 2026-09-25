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
  hoyRegistro: { x: 0.826, y: 0.385 },
  widget: { x: 0.483, y: 0.283 },
  notificacion: { x: 0.306, y: 0.427 },
  repaso: { x: 0.5, y: 0.915 },
  hoyADetalle: { x: 0.2, y: 0.318 },
  detalleAStats: { x: 0.107, y: 0.05 },
  statsALogros: { x: 0.5, y: 0.294 },
  hoyATareas: { x: 0.5, y: 0.815 },
  tareasAFoco: { x: 0.226, y: 0.257 },
} as const satisfies Record<string, Punto>;
