import React from 'react';
import { Composition, Still } from 'remotion';
import { BitoPromo } from './BitoPromo';
import { HabiPlayground } from './habi/HabiPlayground';
import { IconoStill } from './stills/Icono';
import { Ilustracion } from './stills/Ilustracion';
import { FeatureGraphic, Promo } from './stills/Promo';
import { StoreShot } from './stills/StoreShot';
import { ALTO, ANCHO, DURACION_TOTAL, FPS } from './timing';

export const RemotionRoot: React.FC = () => (
  <>
    <Composition id="BitoPromo-es" component={BitoPromo} durationInFrames={DURACION_TOTAL} fps={FPS} width={ANCHO} height={ALTO} defaultProps={{ lang: 'es' as const }} />
    <Composition id="BitoPromo-en" component={BitoPromo} durationInFrames={DURACION_TOTAL} fps={FPS} width={ANCHO} height={ALTO} defaultProps={{ lang: 'en' as const }} />
    <Composition id="HabiPlayground" component={HabiPlayground} durationInFrames={360} fps={FPS} width={ANCHO} height={ALTO} defaultProps={{ gesto: 'todos' as const }} />
    <Still id="Promo" component={Promo} width={1920} height={1080} defaultProps={{ lang: 'es' as const }} />
    <Still id="FeatureGraphic" component={FeatureGraphic} width={1024} height={500} defaultProps={{ lang: 'es' as const }} />
    <Still id="StoreShot" component={StoreShot} width={1080} height={1920} defaultProps={{ lang: 'es' as const, indice: 0 }} />
    <Still id="Icon" component={IconoStill} width={1024} height={1024} />
    <Still id="Ilustracion" component={Ilustracion} width={800} height={800} />
  </>
);
