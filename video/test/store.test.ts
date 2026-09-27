import { describe, expect, it } from 'vitest';
import { CAPTURAS } from '../scripts/validar-store.mjs';
import { STORE_SHOTS } from '../src/stills/storeShots';

describe('capturas de tienda', () => {
  it('son ocho, y la 6 y la 7 son foco y respiración', () => {
    expect(STORE_SHOTS.map((s) => s.pantalla)).toEqual([
      'hoy', 'detalle', 'notificacion', 'widget', 'repaso', 'foco', 'respiracion', 'logros',
    ]);
    expect(STORE_SHOTS).toHaveLength(CAPTURAS);
  });
});
