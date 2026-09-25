import React from 'react';
import { interpolate, useCurrentFrame } from 'remotion';
import { COLOR } from './Fondo';

const CLAMP = { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' } as const;

export const Dedo: React.FC<{ x: number; y: number; toque: number; tamano?: number }> = ({ x, y, toque, tamano = 72 }) => {
  const f = useCurrentFrame() - toque;
  if (f < -8 || f > 14) return null;
  const visible = interpolate(f, [-8, -2], [0, 1], CLAMP) * interpolate(f, [6, 14], [1, 0], CLAMP);
  const presion = interpolate(f, [-2, 0, 4], [1, 0.82, 1], CLAMP);
  const onda = interpolate(f, [0, 12], [0, 1], CLAMP);
  const caja: React.CSSProperties = { position: 'absolute', left: x - tamano / 2, top: y - tamano / 2, width: tamano, height: tamano, borderRadius: '50%' };
  return (
    <>
      <div
        style={{
          ...caja,
          backgroundColor: 'rgba(60, 53, 43, 0.28)',
          border: `3px solid ${COLOR.brillo}`,
          opacity: visible,
          transform: `scale(${presion})`,
        }}
      />
      {f >= 0 ? <div style={{ ...caja, border: `4px solid ${COLOR.hoja}`, opacity: 1 - onda, transform: `scale(${1 + onda * 1.4})` }} /> : null}
    </>
  );
};
