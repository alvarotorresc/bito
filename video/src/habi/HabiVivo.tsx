import React from 'react';
import { useCurrentFrame } from 'remotion';
import { FPS } from '../timing';
import { poseEnFrame, type LineaHabi } from './gestos';
import { Habi } from './Habi';

export const HabiVivo: React.FC<{ linea: LineaHabi; tamano: number; style?: React.CSSProperties }> = ({ linea, tamano, style }) => {
  const frame = useCurrentFrame();
  return <Habi pose={poseEnFrame(frame, FPS, linea)} tamano={tamano} style={style} />;
};
