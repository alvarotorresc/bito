import { describe, expect, it } from 'vitest';
import { PANTALLAS, punto, TOQUES, resolverShot, rutaShot, shotsQueFaltan } from '../src/ui/shots';

describe('shots', () => {
  it('son las 16 pantallas que entrega la pasada de capturas', () => {
    expect(PANTALLAS).toEqual([
      'hoy', 'detalle', 'stats', 'records', 'logros', 'habi', 'tienda',
      'repaso', 'ajustes', 'tareas', 'foco', 'widget', 'notificacion', 'respiracion',
      'hoy-tareas', 'stats-logros',
    ]);
  });

  it('resuelve la ruta solo si el fichero existe', () => {
    expect(rutaShot('es', 'hoy')).toBe('shots/es/hoy.png');
    expect(resolverShot(['shots/es/hoy.png'], 'es', 'hoy')).toBe('shots/es/hoy.png');
    expect(resolverShot(['shots/es/hoy.png'], 'en', 'hoy')).toBeNull();
  });

  it('lista las 32 que faltan cuando no hay ninguna', () => {
    expect(shotsQueFaltan([])).toHaveLength(32);
    expect(shotsQueFaltan(PANTALLAS.flatMap((p) => [rutaShot('es', p), rutaShot('en', p)]))).toEqual([]);
  });

  it.each(Object.entries(TOQUES))('el toque %s cae dentro de la captura', (_, valor) => {
    const puntos = 'x' in valor ? [valor] : Object.values(valor);
    for (const p of puntos) {
      expect(p.x).toBeGreaterThanOrEqual(0);
      expect(p.x).toBeLessThanOrEqual(1);
      expect(p.y).toBeGreaterThanOrEqual(0);
      expect(p.y).toBeLessThanOrEqual(1);
    }
  });

  it('hoyATareas y statsALogros tienen un punto distinto para es y en', () => {
    expect(punto('es', TOQUES.hoyATareas)).not.toEqual(punto('en', TOQUES.hoyATareas));
    expect(punto('es', TOQUES.statsALogros)).not.toEqual(punto('en', TOQUES.statsALogros));
  });
});
