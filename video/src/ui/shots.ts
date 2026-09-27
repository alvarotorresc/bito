import type { Lang } from '../copy';

export const PANTALLAS = [
  'hoy', 'detalle', 'stats', 'records', 'logros', 'habi', 'tienda',
  'repaso', 'ajustes', 'tareas', 'foco', 'widget', 'notificacion', 'respiracion',
  'hoy-tareas', 'stats-logros',
] as const;
export type Pantalla = (typeof PANTALLAS)[number];

export const rutaShot = (lang: Lang, p: Pantalla) => `shots/${lang}/${p}.png`;

export const resolverShot = (disponibles: readonly string[], lang: Lang, p: Pantalla): string | null =>
  disponibles.includes(rutaShot(lang, p)) ? rutaShot(lang, p) : null;

export const shotsQueFaltan = (disponibles: readonly string[]): string[] =>
  (['es', 'en'] as const).flatMap((lang) => PANTALLAS.map((p) => rutaShot(lang, p))).filter((r) => !disponibles.includes(r));

export type Punto = { x: number; y: number };

const TOQUES_COMUNES = {
  hoyRegistro: { x: 0.826, y: 0.385 },
  widget: { x: 0.483, y: 0.283 },
  notificacion: { x: 0.310, y: 0.414 },
  repaso: { x: 0.5, y: 0.915 },
  hoyADetalle: { x: 0.2, y: 0.318 },
  detalleAStats: { x: 0.107, y: 0.05 },
  tareasAFoco: { x: 0.215, y: 0.258 },
  hoyARespiracion: { x: 0.715, y: 0.062 },
} as const satisfies Record<string, Punto>;

// hoyATareas y statsALogros dependen de un scroll que se detiene en una altura distinta
// según el idioma, así que llevan un punto por idioma en vez de uno compartido.
const TOQUES_POR_IDIOMA = {
  hoyATareas: { es: { x: 0.469, y: 0.345 }, en: { x: 0.469, y: 0.262 } },
  statsALogros: { es: { x: 0.493, y: 0.572 }, en: { x: 0.493, y: 0.630 } },
} as const satisfies Record<string, Record<Lang, Punto>>;

export const TOQUES = { ...TOQUES_COMUNES, ...TOQUES_POR_IDIOMA };

/** Resuelve un valor de TOQUES al punto que toca: tal cual si es un Punto, o el del idioma si es uno por idioma. */
export const punto = (lang: Lang, valor: Punto | Record<Lang, Punto>): Punto => ('x' in valor ? valor : valor[lang]);
