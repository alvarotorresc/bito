import { describe, expect, it } from 'vitest';
import {
  CANALES_REPOSO,
  ENTRADA_DESPLAZ,
  INCLINACION_MIRADA,
  canalesGesto,
  duracionGesto,
  poseEnFrame,
  type Gesto,
  type LineaHabi,
} from '../src/habi/gestos';

const FPS = 30;
const GESTOS: Gesto[] = [
  { tipo: 'enter' },
  { tipo: 'react-log' },
  { tipo: 'celebrate' },
  { tipo: 'tap', x: 0.7, y: 0.3, salta: true },
  { tipo: 'tap', x: 0.2, y: 0.8, salta: false },
  { tipo: 'swap-face', cara: 'sargento' },
  { tipo: 'settle' },
];

describe('gestos', () => {
  it.each(GESTOS)('$tipo vuelve al reposo al acabar', (g) => {
    const fin = duracionGesto(g, FPS);
    for (const f of [fin, fin + 1, fin + 30]) {
      const c = canalesGesto(g, f, FPS);
      for (const v of Object.values(c)) expect(Math.abs(v)).toBeLessThan(2e-3);
    }
  });

  it('enter empieza fuera del plano', () => {
    expect(canalesGesto({ tipo: 'enter' }, 0, FPS).desplazY).toBeCloseTo(ENTRADA_DESPLAZ, 6);
  });

  it('react-log dura 320 ms: bote de 0,35 en el frame 6 y en el suelo en el 10', () => {
    expect(canalesGesto({ tipo: 'react-log' }, 6, FPS).salto).toBeCloseTo(0.35, 6);
    expect(canalesGesto({ tipo: 'react-log' }, 10, FPS).salto).toBe(0);
  });

  it('celebrate llega arriba a los 150 ms', () => {
    expect(canalesGesto({ tipo: 'celebrate' }, 5, FPS).salto).toBeCloseTo(1, 6);
  });

  it('tap se inclina alejándose del dedo', () => {
    expect(canalesGesto({ tipo: 'tap', x: 0.8, y: 0.5, salta: false }, 2, FPS).inclinacion).toBeLessThan(0);
    expect(canalesGesto({ tipo: 'tap', x: 0.2, y: 0.5, salta: false }, 2, FPS).inclinacion).toBeGreaterThan(0);
  });

  it('tap en la cabeza aplasta más que en la barriga', () => {
    const cabeza = canalesGesto({ tipo: 'tap', x: 0.5, y: 0.1, salta: false }, 2, FPS).squash;
    const barriga = canalesGesto({ tipo: 'tap', x: 0.5, y: 0.9, salta: false }, 2, FPS).squash;
    expect(cabeza).toBeGreaterThan(barriga);
  });

  it('tap con salto llega a 0,75 y mantiene la inclinación en el aire', () => {
    const c = canalesGesto({ tipo: 'tap', x: 0.8, y: 0.3, salta: true }, 7, FPS);
    expect(c.salto).toBeCloseTo(0.75, 6);
    expect(c.inclinacion).toBeCloseTo(-6, 6);
  });
});

describe('poseEnFrame', () => {
  const quieta: LineaHabi = { semilla: 'quieta', eventos: [] };

  it('sin eventos no hay gesto ni mirada', () => {
    const p = poseEnFrame(300, FPS, quieta);
    expect({ squash: p.squash, salto: p.salto, inclinacion: p.inclinacion, desplazY: p.desplazY }).toEqual(CANALES_REPOSO);
    expect(p.miradaX).toBe(0);
    expect(p.cara).toBe('neutra');
    expect(p.mezclaCara).toBe(1);
  });

  it('los ojos van primero y el cuerpo después', () => {
    const linea: LineaHabi = { semilla: 'mira', eventos: [{ at: 10, mirada: 'derecha' }] };
    const p = poseEnFrame(13, FPS, linea);
    expect(p.miradaX).toBeGreaterThan(0.3);
    expect(p.inclinacion / INCLINACION_MIRADA).toBeLessThan(p.miradaX / 4);
    const tarde = poseEnFrame(80, FPS, linea);
    expect(tarde.miradaX).toBeCloseTo(1, 2);
    expect(tarde.inclinacion).toBeCloseTo(INCLINACION_MIRADA, 1);
  });

  it('una mirada nueva a mitad de otra parte de donde estaban los ojos', () => {
    const linea: LineaHabi = { semilla: 'mira', eventos: [{ at: 10, mirada: 'derecha' }, { at: 13, mirada: 'izquierda' }] };
    const antes = poseEnFrame(13, FPS, { semilla: 'mira', eventos: [{ at: 10, mirada: 'derecha' }] }).miradaX;
    expect(poseEnFrame(13, FPS, linea).miradaX).toBeCloseTo(antes, 6);
  });

  it('swap-face funde la cara en 300 ms', () => {
    const linea: LineaHabi = { semilla: 'cara', eventos: [{ at: 20, gesto: { tipo: 'swap-face', cara: 'sargento' } }] };
    const inicio = poseEnFrame(20, FPS, linea);
    expect(inicio.cara).toBe('sargento');
    expect(inicio.caraPrevia).toBe('neutra');
    expect(inicio.mezclaCara).toBe(0);
    expect(poseEnFrame(29, FPS, linea).mezclaCara).toBeGreaterThan(0.95);
    expect(poseEnFrame(60, FPS, linea).mezclaCara).toBe(1);
  });

  it('un toque hace parpadear', () => {
    const linea: LineaHabi = { semilla: 'toque', eventos: [{ at: 20, gesto: { tipo: 'tap', x: 0.5, y: 0.5, salta: false } }] };
    const maximo = Math.max(...[21, 22, 23, 24].map((f) => poseEnFrame(f, FPS, linea).parpado));
    expect(maximo).toBeGreaterThan(0.9);
  });

  it('es reproducible', () => {
    const linea: LineaHabi = { semilla: 'r', animoInicial: 'radiante', eventos: [{ at: 5, gesto: { tipo: 'celebrate' } }] };
    expect(poseEnFrame(500, FPS, linea)).toEqual(poseEnFrame(500, FPS, linea));
  });
});
