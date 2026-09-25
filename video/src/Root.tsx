import React from 'react';
import { Composition } from 'remotion';
import { BitoPromo } from './BitoPromo';
import { HabiPlayground } from './habi/HabiPlayground';
import { ALTO, ANCHO, DURACION_TOTAL, FPS } from './timing';

export const RemotionRoot: React.FC = () => (
  <>
    <Composition id="BitoPromo-es" component={BitoPromo} durationInFrames={DURACION_TOTAL} fps={FPS} width={ANCHO} height={ALTO} defaultProps={{ lang: 'es' as const }} />
    <Composition id="BitoPromo-en" component={BitoPromo} durationInFrames={DURACION_TOTAL} fps={FPS} width={ANCHO} height={ALTO} defaultProps={{ lang: 'en' as const }} />
    <Composition id="HabiPlayground" component={HabiPlayground} durationInFrames={360} fps={FPS} width={ANCHO} height={ALTO} defaultProps={{ gesto: 'todos' as const }} />
  </>
);
