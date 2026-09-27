import React from 'react';
import { AbsoluteFill } from 'remotion';
import { texto, type Lang } from '../copy';
import { Fondo } from '../ui/Fondo';
import { Phone, medidasPhone } from '../ui/Phone';
import { Rotulo } from '../ui/Rotulo';
import { STORE_SHOTS } from './storeShots';

export { STORE_SHOTS };

export type StoreShotProps = { lang: Lang; indice: number };

const ALTO_MOVIL = 1640;
const ARRIBA_MOVIL = 240;
const m = medidasPhone(ALTO_MOVIL);

export const StoreShot: React.FC<StoreShotProps> = ({ lang, indice }) => {
  const shot = STORE_SHOTS[indice];
  if (!shot) throw new Error(`StoreShot: índice ${indice} fuera de 0..${STORE_SHOTS.length - 1}`);
  return (
    <AbsoluteFill>
      <Fondo />
      <Rotulo tamano={68} alinear="center" style={{ left: 60, width: 960, top: 52 }}>
        {texto(lang, shot.titular)}
      </Rotulo>
      <Phone lang={lang} pantalla={shot.pantalla} alto={ALTO_MOVIL} style={{ left: (1080 - m.anchoTotal) / 2, top: ARRIBA_MOVIL }} />
    </AbsoluteFill>
  );
};
