import { describe, expect, it } from 'vitest';
import { PANTALLAS, TOQUES, resolverShot, rutaShot, shotsQueFaltan } from '../src/ui/shots';

describe('shots', () => {
  it('son las 13 pantallas que entrega el plan de capturas', () => {
    expect(PANTALLAS).toEqual([
      'hoy', 'detalle', 'stats', 'records', 'logros', 'habi', 'tienda',
      'repaso', 'ajustes', 'tareas', 'foco', 'widget', 'notificacion',
    ]);
  });

  it('resuelve la ruta solo si el fichero existe', () => {
    expect(rutaShot('es', 'hoy')).toBe('shots/es/hoy.png');
    expect(resolverShot(['shots/es/hoy.png'], 'es', 'hoy')).toBe('shots/es/hoy.png');
    expect(resolverShot(['shots/es/hoy.png'], 'en', 'hoy')).toBeNull();
  });

  it('lista las 26 que faltan cuando no hay ninguna', () => {
    expect(shotsQueFaltan([])).toHaveLength(26);
    expect(shotsQueFaltan(PANTALLAS.flatMap((p) => [rutaShot('es', p), rutaShot('en', p)]))).toEqual([]);
  });

  it.each(Object.entries(TOQUES))('el toque %s cae dentro de la captura', (_, punto) => {
    expect(punto.x).toBeGreaterThanOrEqual(0);
    expect(punto.x).toBeLessThanOrEqual(1);
    expect(punto.y).toBeGreaterThanOrEqual(0);
    expect(punto.y).toBeLessThanOrEqual(1);
  });
});
