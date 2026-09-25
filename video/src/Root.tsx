import React from 'react';
import { AbsoluteFill, Composition } from 'remotion';
import { Habi, POSE_REPOSO } from './habi/Habi';
import { ALTO, ANCHO, FPS } from './timing';

const HabiEstatica: React.FC = () => (
  <AbsoluteFill style={{ backgroundColor: '#F2ECE1', flexDirection: 'row', alignItems: 'center', justifyContent: 'space-evenly' }}>
    <Habi pose={POSE_REPOSO} tamano={420} />
    <Habi pose={{ ...POSE_REPOSO, cara: 'sargento', caraPrevia: 'sargento' }} tamano={420} />
    <Habi
      pose={{ ...POSE_REPOSO, cara: 'cheerleader', caraPrevia: 'cheerleader', parpado: 0.5, miradaX: 1, squash: 0.35, inclinacion: 8 }}
      tamano={420}
    />
  </AbsoluteFill>
);

export const RemotionRoot: React.FC = () => (
  <>
    <Composition id="HabiPlayground" component={HabiEstatica} durationInFrames={1} fps={FPS} width={ANCHO} height={ALTO} />
  </>
);
