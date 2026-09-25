import React from 'react';
import { AbsoluteFill } from 'remotion';
import { texto, type PromoProps } from '../copy';
import type { LineaHabi } from '../habi/gestos';
import { HabiVivo } from '../habi/HabiVivo';
import { momentoLocal } from '../timing';
import { Fondo } from '../ui/Fondo';
import { Rotulo } from '../ui/Rotulo';

export const LINEA_01: LineaHabi = {
  semilla: 'escena-01',
  eventos: [
    { at: 0, gesto: { tipo: 'enter' } },
    { at: 0, mirada: 'abajo' },
    { at: momentoLocal('miraCamara'), mirada: 'camara' },
  ],
};

export const Escena01: React.FC<PromoProps> = ({ lang }) => (
  <AbsoluteFill>
    <Fondo />
    <HabiVivo linea={LINEA_01} tamano={560} style={{ position: 'absolute', left: 680, top: 340 }} />
    <Rotulo entra={40} tamano={112} alinear="center" style={{ left: 0, right: 0, top: 150 }}>
      {texto(lang, 'frase')}
    </Rotulo>
  </AbsoluteFill>
);
