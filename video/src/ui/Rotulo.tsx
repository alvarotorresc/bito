import { loadFont } from '@remotion/google-fonts/Outfit';
import React from 'react';
import { Easing, interpolate, useCurrentFrame } from 'remotion';
import { COLOR } from './Fondo';

const { fontFamily } = loadFont('normal', { weights: ['400', '600', '700'], subsets: ['latin'] });

export const FUENTE = fontFamily;
export const SALIDA = Easing.bezier(0.22, 0.61, 0.36, 1);
const CLAMP = { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' } as const;

export type RotuloProps = {
  children: React.ReactNode;
  entra?: number;
  sale?: number;
  tamano?: number;
  peso?: 400 | 600 | 700;
  color?: string;
  alinear?: 'left' | 'center' | 'right';
  style?: React.CSSProperties;
};

export const Rotulo: React.FC<RotuloProps> = ({
  children,
  entra = -8,
  sale,
  tamano = 64,
  peso = 700,
  color = COLOR.tinta,
  alinear = 'left',
  style,
}) => {
  const frame = useCurrentFrame();
  const dentro = interpolate(frame, [entra, entra + 8], [0, 1], { ...CLAMP, easing: SALIDA });
  const fuera = sale === undefined ? 1 : interpolate(frame, [sale, sale + 6], [1, 0], CLAMP);
  return (
    <div
      style={{
        position: 'absolute',
        fontFamily: FUENTE,
        fontSize: tamano,
        fontWeight: peso,
        lineHeight: 1.15,
        letterSpacing: '-0.01em',
        color,
        textAlign: alinear,
        opacity: dentro * fuera,
        transform: `translateY(${(1 - dentro) * 24}px)`,
        ...style,
      }}
    >
      {children}
    </div>
  );
};
