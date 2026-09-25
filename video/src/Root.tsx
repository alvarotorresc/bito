import React from 'react';
import { Composition, Still } from 'remotion';
import { BitoPromo } from './BitoPromo';
import { HabiPlayground } from './habi/HabiPlayground';
import { IconoStill } from './stills/Icono';
import { Ilustracion } from './stills/Ilustracion';
import { ALTO, ANCHO, DURACION_TOTAL, FPS } from './timing';

export const RemotionRoot: React.FC = () => (
  <>
    <Composition id="BitoPromo-es" component={BitoPromo} durationInFrames={DURACION_TOTAL} fps={FPS} width={ANCHO} height={ALTO} defaultProps={{ lang: 'es' as const }} />
    <Composition id="BitoPromo-en" component={BitoPromo} durationInFrames={DURACION_TOTAL} fps={FPS} width={ANCHO} height={ALTO} defaultProps={{ lang: 'en' as const }} />
    <Composition id="HabiPlayground" component={HabiPlayground} durationInFrames={360} fps={FPS} width={ANCHO} height={ALTO} defaultProps={{ gesto: 'todos' as const }} />
    <Still id="Icon" component={IconoStill} width={1024} height={1024} />
    <Still id="Ilustracion" component={Ilustracion} width={800} height={800} />
  </>
);
