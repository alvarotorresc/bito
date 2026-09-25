import React from 'react';
import { AbsoluteFill } from 'remotion';
import { Habi, POSE_REPOSO } from '../habi/Habi';
import { COLOR } from '../ui/Fondo';

export const Ilustracion: React.FC = () => (
  <AbsoluteFill style={{ backgroundColor: COLOR.papel, alignItems: 'center', justifyContent: 'center' }}>
    <Habi pose={POSE_REPOSO} tamano={640} />
  </AbsoluteFill>
);
