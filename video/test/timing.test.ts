import { describe, expect, it } from 'vitest';
import {
  DURACION_TOTAL,
  ESCENAS,
  FPS,
  MOMENTOS,
  escena,
  momentoGlobal,
  msAFrames,
  type Momento,
} from '../src/timing';

describe('timing', () => {
  it('las diez escenas suman 2070 frames', () => {
    expect(ESCENAS.reduce((suma, e) => suma + e.duracion, 0)).toBe(DURACION_TOTAL);
    expect(DURACION_TOTAL).toBe(2070);
  });

  it('la duración cae entre 45 y 70 s', () => {
    const segundos = DURACION_TOTAL / FPS;
    expect(segundos).toBeGreaterThanOrEqual(45);
    expect(segundos).toBeLessThanOrEqual(70);
  });

  it('cada escena empieza donde acaba la anterior y en el segundo del guion', () => {
    ESCENAS.forEach((e, i) => {
      const previa = ESCENAS[i - 1];
      expect(e.desde).toBe(previa ? previa.desde + previa.duracion : 0);
    });
    expect(ESCENAS.map((e) => e.desde / FPS)).toEqual([0, 4, 10, 14, 20, 30, 46, 52, 57, 63]);
    const ultima = ESCENAS[ESCENAS.length - 1]!;
    expect((ultima.desde + ultima.duracion) / FPS).toBe(69);
  });

  it.each(Object.keys(MOMENTOS) as Momento[])('%s cae dentro de su escena', (m) => {
    const { escena: n, local } = MOMENTOS[m];
    expect(local).toBeGreaterThanOrEqual(0);
    expect(local).toBeLessThan(escena(n).duracion);
  });

  it('momentoGlobal suma el inicio de la escena', () => {
    expect(momentoGlobal('saludo')).toBe(20);
    expect(momentoGlobal('maullido2')).toBe(1890 + 62);
    expect(momentoGlobal('toqueRespiracion')).toBe(1605);
    expect(ESCENAS.map((e) => e.nombre).slice(6)).toEqual(['tareas', 'respiracion', 'promesas', 'cierre']);
  });

  it('msAFrames convierte las duraciones del spec', () => {
    expect(msAFrames(320)).toBe(10);
    expect(msAFrames(520)).toBe(16);
    expect(msAFrames(300)).toBe(9);
    expect(msAFrames(280)).toBe(8);
    expect(msAFrames(150)).toBe(5);
    expect(msAFrames(140)).toBe(4);
    expect(msAFrames(70)).toBe(2);
  });
});
