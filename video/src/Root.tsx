import React from 'react';
import { Composition } from 'remotion';
import { HabiPlayground } from './habi/HabiPlayground';
import { ALTO, ANCHO, FPS } from './timing';

export const RemotionRoot: React.FC = () => (
  <>
    <Composition
      id="HabiPlayground"
      component={HabiPlayground}
      durationInFrames={360}
      fps={FPS}
      width={ANCHO}
      height={ALTO}
      defaultProps={{ gesto: 'todos' as const }}
    />
  </>
);
