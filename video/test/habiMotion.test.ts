import { describe, expect, it } from 'vitest';
import {
  MUELLES,
  PARPADO_ABRE_MS,
  PARPADO_CIERRA_MS,
  agendaParpadeos,
  dampingDesdeRatio,
  parpado,
  respiracion,
  sombra,
} from '../src/habi/habiMotion';
import { framesAMs, msAFrames } from '../src/timing';

const FPS = 30;

describe('muelles', () => {
  it('convierte ratio y rigidez en damping', () => {
    expect(dampingDesdeRatio(0.32, 380)).toBeCloseTo(12.476, 3);
    expect(dampingDesdeRatio(0.6, 400)).toBeCloseTo(24, 6);
    expect(dampingDesdeRatio(0.85, 300)).toBeCloseTo(29.445, 3);
    expect(dampingDesdeRatio(0.8, 260)).toBeCloseTo(25.799, 3);
  });

  it('cada muelle conserva el ratio de la app', () => {
    const ratio = (c: { damping: number; stiffness: number; mass: number }) => c.damping / (2 * Math.sqrt(c.stiffness * c.mass));
    expect(ratio(MUELLES.gelatina)).toBeCloseTo(0.32, 6);
    expect(ratio(MUELLES.asentarse)).toBeCloseTo(0.6, 6);
    expect(ratio(MUELLES.mirada)).toBeCloseTo(0.85, 6);
    expect(ratio(MUELLES.cara)).toBeCloseTo(0.8, 6);
  });
});

describe('respiracion', () => {
  it('empieza en 0 y llega a 1 al cabo de un periodo', () => {
    expect(respiracion(0, FPS, 'normal')).toBeCloseTo(0, 6);
    expect(respiracion(msAFrames(2700), FPS, 'normal')).toBeCloseTo(1, 6);
  });

  it.each([
    ['radiante', 120],
    ['normal', 162],
    ['bajo', 210],
  ] as const)('el ciclo de %s dura 2 × periodo (%i frames)', (animo, ciclo) => {
    for (const f of [0, 7, 33, 80, 101]) {
      expect(respiracion(f + ciclo, FPS, animo)).toBeCloseTo(respiracion(f, FPS, animo), 6);
    }
  });

  it('se queda en [0, 1]', () => {
    for (let f = 0; f < 600; f++) {
      const v = respiracion(f, FPS, 'bajo');
      expect(v).toBeGreaterThanOrEqual(0);
      expect(v).toBeLessThanOrEqual(1);
    }
  });
});

describe('parpadeo', () => {
  const agenda = agendaParpadeos('habi', 600_000, FPS);

  it('la misma semilla da la misma agenda y otra semilla da otra', () => {
    expect(agendaParpadeos('habi', 600_000, FPS)).toEqual(agenda);
    expect(agendaParpadeos('otra', 600_000, FPS)).not.toEqual(agenda);
  });

  it('el pico de cada parpadeo cae en un frame y cierra del todo', () => {
    for (const inicio of agenda) {
      const framePico = msAFrames(inicio + PARPADO_CIERRA_MS);
      expect(parpado(framesAMs(framePico), agenda)).toBeCloseTo(1, 5);
    }
  });

  it('los intervalos son de 3 a 5 s más el parpadeo, o el hueco corto de un doble', () => {
    const cicloMs = PARPADO_CIERRA_MS + PARPADO_ABRE_MS;
    const frame = 1000 / FPS;
    let dobles = 0;
    for (let i = 1; i < agenda.length; i++) {
      const d = agenda[i]! - agenda[i - 1]!;
      const esDoble = d < 1000;
      if (esDoble) {
        dobles++;
        expect(d).toBeGreaterThanOrEqual(cicloMs + 90 - frame);
        expect(d).toBeLessThanOrEqual(cicloMs + 90 + frame);
      } else {
        expect(d).toBeGreaterThanOrEqual(3000 + cicloMs - frame);
        expect(d).toBeLessThanOrEqual(5000 + cicloMs + frame);
      }
    }
    const proporcion = dobles / (agenda.length - dobles);
    expect(proporcion).toBeGreaterThan(0.12);
    expect(proporcion).toBeLessThan(0.4);
  });

  it('fuera de un parpadeo el ojo está abierto', () => {
    const primero = agenda[0]!;
    expect(parpado(primero - 1, agenda)).toBe(0);
    expect(parpado(primero + PARPADO_CIERRA_MS + PARPADO_ABRE_MS + 1, agenda)).toBe(0);
  });
});

describe('sombra', () => {
  it('en reposo tiene escala y opacidad 1', () => {
    expect(sombra({ salto: 0, squash: 0, desplazY: 0 })).toEqual({ escalaX: 1, opacidad: 1 });
  });

  it('se estrecha y aclara al subir', () => {
    const s = sombra({ salto: 1, squash: 0, desplazY: 0 });
    expect(s.escalaX).toBeLessThan(1);
    expect(s.opacidad).toBeLessThan(1);
  });

  it('se ensancha al aplastar', () => {
    expect(sombra({ salto: 0, squash: 0.35, desplazY: 0 }).escalaX).toBeGreaterThan(1);
  });

  it('desaparece mientras Habi está fuera del plano', () => {
    expect(sombra({ salto: 0, squash: 0, desplazY: 0.3 }).opacidad).toBe(0);
  });
});
