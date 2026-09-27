import React from 'react';
import { AbsoluteFill, Sequence } from 'remotion';
import { texto, type PromoProps } from '../copy';
import { poseEnFrame } from '../habi/gestos';
import { Habi } from '../habi/Habi';
import { Escena02, LINEA_02 } from '../scenes/02-QueEs';
import { FPS } from '../timing';
import { Fondo } from '../ui/Fondo';
import { Phone, medidasPhone } from '../ui/Phone';
import { Rotulo } from '../ui/Rotulo';

export const PROMO_FRAME = 170;

export const Promo: React.FC<PromoProps> = ({ lang }) => {
  if (poseEnFrame(PROMO_FRAME, FPS, LINEA_02).parpado > 0) {
    throw new Error(`Promo: Habi está parpadeando en el frame ${PROMO_FRAME}; elige otro PROMO_FRAME`);
  }
  return (
    <Sequence from={-PROMO_FRAME}>
      <Escena02 lang={lang} />
    </Sequence>
  );
};

// Feature graphic de Play (1024x500): tres piezas con el mismo centro vertical, y = 250.
// Habi no se centra por su caja: el cuerpo ocupa de 0,13 a 0,97 del lado, así que su centro
// visible cae a 0,55 del lado (154 px con 280). ARRIBA_HABI se calibra con medir-fg.py.
const FG = { ancho: 1024, alto: 500, centro: 250, margen: 40 };
const HABI_FG = { tamano: 280, izq: 40, arriba: 96 };
const TEXTO_FG = { izq: 330, ancho: 380, tamano: 44 };
const ALTO_MOVIL_FG = 440;
const mFG = medidasPhone(ALTO_MOVIL_FG);

export const FeatureGraphic: React.FC<PromoProps> = ({ lang }) => (
  <AbsoluteFill>
    <Fondo />
    <Habi
      pose={poseEnFrame(PROMO_FRAME, FPS, LINEA_02)}
      tamano={HABI_FG.tamano}
      style={{ position: 'absolute', left: HABI_FG.izq, top: HABI_FG.arriba }}
    />
    <div style={{ position: 'absolute', left: TEXTO_FG.izq, top: 0, width: TEXTO_FG.ancho, height: FG.alto, display: 'flex', alignItems: 'center' }}>
      <Rotulo tamano={TEXTO_FG.tamano} style={{ position: 'relative', width: TEXTO_FG.ancho }}>
        {texto(lang, 'descriptor.1')}
      </Rotulo>
    </div>
    <Phone
      lang={lang}
      pantalla="hoy"
      alto={ALTO_MOVIL_FG}
      style={{ left: FG.ancho - FG.margen - mFG.anchoTotal, top: FG.centro - mFG.altoTotal / 2 }}
    />
  </AbsoluteFill>
);
