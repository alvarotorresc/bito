import { describe, expect, it } from 'vitest';
import { CUES, DURACION_SONIDO, MAULLIDOS, volumenMusica } from '../src/audio/mezcla';
import { DURACION_TOTAL, ESCENAS, momentoGlobal } from '../src/timing';

const maullidos = CUES.filter((c) => MAULLIDOS.includes(c.sonido));

describe('mezcla', () => {
  it('el volumen de la música siempre está en [0, 1]', () => {
    for (let f = 0; f < DURACION_TOTAL; f++) {
      const v = volumenMusica(f);
      expect(v).toBeGreaterThanOrEqual(0);
      expect(v).toBeLessThanOrEqual(1);
    }
  });

  it('entra desde 0 y cierra en 0', () => {
    expect(volumenMusica(0)).toBe(0);
    expect(volumenMusica(60)).toBeGreaterThanOrEqual(0.5);
    expect(volumenMusica(DURACION_TOTAL - 1)).toBeLessThanOrEqual(0.01);
  });

  it.each(maullidos.map((c) => [c.momento, c] as const))('baja bajo el maullido de %s', (_, c) => {
    const inicio = momentoGlobal(c.momento);
    for (let f = inicio; f < inicio + DURACION_SONIDO[c.sonido]; f++) expect(volumenMusica(f)).toBeLessThanOrEqual(0.3);
  });

  it('baja en la sección oscura, que es la de promesas', () => {
    const promesas = ESCENAS.find((e) => e.nombre === 'promesas')!;
    expect(promesas.n).toBe(9);
    for (let f = promesas.desde + 20; f < promesas.desde + promesas.duracion - 20; f++) {
      expect(volumenMusica(f)).toBeLessThanOrEqual(0.35);
      expect(volumenMusica(f)).toBeGreaterThan(0.1);
    }
  });

  it('ningún sonido se sale del vídeo y dos maullidos no se pisan', () => {
    for (const c of CUES) expect(momentoGlobal(c.momento) + DURACION_SONIDO[c.sonido]).toBeLessThan(DURACION_TOTAL);
    const tramos = maullidos.map((c) => [momentoGlobal(c.momento), momentoGlobal(c.momento) + DURACION_SONIDO[c.sonido]] as const).sort((a, b) => a[0] - b[0]);
    for (let i = 1; i < tramos.length; i++) expect(tramos[i]![0]).toBeGreaterThanOrEqual(tramos[i - 1]![1]);
  });
});
