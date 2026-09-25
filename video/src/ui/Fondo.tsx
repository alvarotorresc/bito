import React from 'react';
import { AbsoluteFill } from 'remotion';

export const COLOR = {
  papel: '#F2ECE1',
  tinta: '#3C352B',
  hoja: '#57A06B',
  salvia: '#A9C9A1',
  oscuro: '#332D25',
  borde: '#E7DFD1',
  brillo: '#FBF8F2',
  placeholder: '#B9B3A8',
} as const;

export const Fondo: React.FC<{ tono?: 'papel' | 'oscuro' }> = ({ tono = 'papel' }) => (
  <AbsoluteFill style={{ backgroundColor: tono === 'papel' ? COLOR.papel : COLOR.oscuro }} />
);
