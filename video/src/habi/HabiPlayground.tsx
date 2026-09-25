import React from 'react';
import { AbsoluteFill, useCurrentFrame } from 'remotion';
import type { EventoHabi, LineaHabi } from './gestos';
import { HabiVivo } from './HabiVivo';

export type GestoPlayground = 'todos' | 'enter' | 'react-log' | 'celebrate' | 'tap' | 'swap-face' | 'settle' | 'mirada';
export type PlaygroundProps = { gesto: GestoPlayground };

const cada60 = (crear: (at: number, i: number) => EventoHabi): EventoHabi[] => Array.from({ length: 6 }, (_, i) => crear(10 + i * 60, i));
const CARAS_CICLO = ['sargento', 'cheerleader', 'neutra'] as const;

const LINEAS: Record<GestoPlayground, LineaHabi> = {
  todos: {
    semilla: 'playground',
    eventos: [
      { at: 0, gesto: { tipo: 'enter' } },
      { at: 40, mirada: 'derecha' },
      { at: 70, mirada: 'camara' },
      { at: 90, gesto: { tipo: 'react-log' } },
      { at: 130, gesto: { tipo: 'celebrate' } },
      { at: 164, mirada: 'dedo' },
      { at: 170, gesto: { tipo: 'tap', x: 0.7, y: 0.3, salta: true } },
      { at: 200, mirada: 'camara' },
      { at: 220, gesto: { tipo: 'swap-face', cara: 'sargento' } },
      { at: 260, gesto: { tipo: 'swap-face', cara: 'cheerleader' } },
      { at: 300, gesto: { tipo: 'swap-face', cara: 'neutra' } },
      { at: 330, gesto: { tipo: 'settle' } },
    ],
  },
  enter: { semilla: 'p-enter', eventos: cada60((at) => ({ at, gesto: { tipo: 'enter' } })) },
  'react-log': { semilla: 'p-log', eventos: cada60((at) => ({ at, gesto: { tipo: 'react-log' } })) },
  celebrate: { semilla: 'p-celebra', animoInicial: 'radiante', eventos: cada60((at) => ({ at, gesto: { tipo: 'celebrate' } })) },
  tap: { semilla: 'p-tap', eventos: cada60((at, i) => ({ at, gesto: { tipo: 'tap', x: i % 2 ? 0.3 : 0.7, y: 0.3, salta: true } })) },
  'swap-face': { semilla: 'p-cara', eventos: cada60((at, i) => ({ at, gesto: { tipo: 'swap-face', cara: CARAS_CICLO[i % 3]! } })) },
  settle: { semilla: 'p-settle', eventos: cada60((at) => ({ at, gesto: { tipo: 'settle' } })) },
  mirada: {
    semilla: 'p-mirada',
    eventos: [
      { at: 10, mirada: 'derecha' },
      { at: 60, mirada: 'izquierda' },
      { at: 110, mirada: 'arriba' },
      { at: 160, mirada: 'abajo' },
      { at: 210, mirada: 'dedo' },
      { at: 260, mirada: 'camara' },
    ],
  },
};

export const HabiPlayground: React.FC<PlaygroundProps> = ({ gesto }) => {
  const frame = useCurrentFrame();
  return (
    <AbsoluteFill style={{ backgroundColor: '#F2ECE1' }}>
      <HabiVivo linea={LINEAS[gesto]} tamano={640} style={{ position: 'absolute', left: 640, top: 300 }} />
      <div style={{ position: 'absolute', left: 40, top: 30, fontFamily: 'monospace', fontSize: 32, color: '#3C352B' }}>
        {`${gesto} · frame ${frame}`}
      </div>
    </AbsoluteFill>
  );
};
