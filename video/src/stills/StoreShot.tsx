import React from 'react';
import { AbsoluteFill } from 'remotion';
import { texto, type CopyKey, type Lang } from '../copy';
import { Fondo } from '../ui/Fondo';
import { Phone, medidasPhone } from '../ui/Phone';
import { Rotulo } from '../ui/Rotulo';
import type { Pantalla } from '../ui/shots';

export const STORE_SHOTS: readonly { pantalla: Pantalla; titular: CopyKey }[] = [
  { pantalla: 'hoy', titular: 'frase' },
  { pantalla: 'notificacion', titular: 'promesa.notificacion' },
  { pantalla: 'stats', titular: 'hace.logros' },
  { pantalla: 'widget', titular: 'promesa.widget' },
  { pantalla: 'logros', titular: 'hace.rachas' },
  { pantalla: 'repaso', titular: 'promesa.repaso' },
];

export type StoreShotProps = { lang: Lang; indice: number };

const ALTO_MOVIL = 1380;
const m = medidasPhone(ALTO_MOVIL);

export const StoreShot: React.FC<StoreShotProps> = ({ lang, indice }) => {
  const shot = STORE_SHOTS[indice];
  if (!shot) throw new Error(`StoreShot: índice ${indice} fuera de 0..${STORE_SHOTS.length - 1}`);
  return (
    <AbsoluteFill>
      <Fondo />
      <Rotulo tamano={76} alinear="center" style={{ left: 80, width: 920, top: 110 }}>
        {texto(lang, shot.titular)}
      </Rotulo>
      <Phone lang={lang} pantalla={shot.pantalla} alto={ALTO_MOVIL} style={{ left: (1080 - m.anchoTotal) / 2, top: 420 }} />
    </AbsoluteFill>
  );
};
