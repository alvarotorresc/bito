import React from 'react';
import { AbsoluteFill } from 'remotion';
import { texto, type PromoProps } from '../copy';
import { poseEnFrame } from '../habi/gestos';
import { Habi } from '../habi/Habi';
import { LINEA_02 } from '../scenes/02-QueEs';
import { ALTO, ANCHO, FPS } from '../timing';
import { COLOR, Fondo } from '../ui/Fondo';
import { Phone, medidasPhone } from '../ui/Phone';
import { Rotulo } from '../ui/Rotulo';

export const PROMO_FRAME = 170;

// Promo horizontal de tienda y web (1920x1080): mismo patrón que la feature graphic, tres
// piezas con el mismo centro vertical, y = 540. Habi no se centra por su caja: su centro
// visible cae a 0,55 del lado (231 px con 420), igual que en la feature graphic.
const PR = { ancho: ANCHO, alto: ALTO, centro: ALTO / 2, margen: 130 };
const HABI_PROMO = { tamano: 420, izq: PR.margen, arriba: PR.centro - 0.55 * 420 };
const TEXTO_PROMO = { izq: 613, ancho: 680, tamanoTitulo: 60, tamanoLinea: 40 };
const ALTO_MOVIL_PROMO = 860;
const mPromo = medidasPhone(ALTO_MOVIL_PROMO);

export const Promo: React.FC<PromoProps> = ({ lang }) => {
  const pose = poseEnFrame(PROMO_FRAME, FPS, LINEA_02);
  if (pose.parpado > 0) {
    throw new Error(`Promo: Habi está parpadeando en el frame ${PROMO_FRAME}; elige otro PROMO_FRAME`);
  }
  const frases = texto(lang, 'descriptor.2').split(/(?<=\.)\s+/);
  return (
    <AbsoluteFill>
      <Fondo />
      <Habi
        pose={pose}
        tamano={HABI_PROMO.tamano}
        style={{ position: 'absolute', left: HABI_PROMO.izq, top: HABI_PROMO.arriba }}
      />
      <div style={{ position: 'absolute', left: TEXTO_PROMO.izq, top: 0, width: TEXTO_PROMO.ancho, height: PR.alto, display: 'flex', alignItems: 'center' }}>
        <div style={{ position: 'relative', width: TEXTO_PROMO.ancho }}>
          <Rotulo tamano={TEXTO_PROMO.tamanoTitulo} style={{ position: 'relative', width: TEXTO_PROMO.ancho }}>
            {texto(lang, 'descriptor.1')}
          </Rotulo>
          {frases.map((frase, i) => (
            <Rotulo
              key={frase}
              tamano={TEXTO_PROMO.tamanoLinea}
              peso={600}
              color={COLOR.hoja}
              style={{ position: 'relative', width: TEXTO_PROMO.ancho, marginTop: i === 0 ? 40 : 16 }}
            >
              {frase}
            </Rotulo>
          ))}
        </div>
      </div>
      <Phone
        lang={lang}
        pantalla="hoy"
        alto={ALTO_MOVIL_PROMO}
        style={{ left: PR.ancho - PR.margen - mPromo.anchoTotal, top: PR.centro - mPromo.altoTotal / 2 }}
      />
    </AbsoluteFill>
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
