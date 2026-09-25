import React from 'react';
import { AbsoluteFill, Sequence } from 'remotion';
import type { PromoProps } from '../copy';
import { poseEnFrame } from '../habi/gestos';
import { Escena02, LINEA_02 } from '../scenes/02-QueEs';
import { ALTO, ANCHO, FPS } from '../timing';
import { Fondo } from '../ui/Fondo';

export const PROMO_FRAME = 170;
const ESCALA_FG = 500 / ALTO;

export const Promo: React.FC<PromoProps> = ({ lang }) => {
  if (poseEnFrame(PROMO_FRAME, FPS, LINEA_02).parpado > 0) {
    throw new Error(`Promo: Habi está parpadeando en el frame ${PROMO_FRAME}; elige otro PROMO_FRAME`);
  }
  return (
    <Sequence from={-PROMO_FRAME}>
      <Escena02 lang={lang} />
    </Sequence>
  );
};

export const FeatureGraphic: React.FC<PromoProps> = ({ lang }) => (
  <AbsoluteFill>
    <Fondo />
    <div
      style={{
        position: 'absolute',
        left: (1024 - ANCHO * ESCALA_FG) / 2,
        top: 0,
        width: ANCHO,
        height: ALTO,
        transform: `scale(${ESCALA_FG})`,
        transformOrigin: '0 0',
      }}
    >
      <Promo lang={lang} />
    </div>
  </AbsoluteFill>
);
