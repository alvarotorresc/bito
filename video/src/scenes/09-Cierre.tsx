import React from 'react';
import { AbsoluteFill, interpolate, spring, useCurrentFrame } from 'remotion';
import { texto, type PromoProps } from '../copy';
import { poseEnFrame, type LineaHabi } from '../habi/gestos';
import { MUELLES } from '../habi/habiMotion';
import { Icono } from '../stills/Icono';
import { FPS, momentoLocal } from '../timing';
import { COLOR, Fondo } from '../ui/Fondo';
import { Rotulo } from '../ui/Rotulo';

const TAM_ICONO = 340;

export const LINEA_09: LineaHabi = {
  semilla: 'escena-09',
  eventos: [
    { at: momentoLocal('asentarse'), gesto: { tipo: 'settle' } },
    { at: momentoLocal('maullido1'), gesto: { tipo: 'react-log' } },
    { at: momentoLocal('maullido2'), gesto: { tipo: 'react-log' } },
  ],
};

export const Escena09: React.FC<PromoProps> = ({ lang }) => {
  const frame = useCurrentFrame();
  const pose = poseEnFrame(frame, FPS, LINEA_09);
  const aparece = spring({ frame, fps: FPS, config: MUELLES.asentarse });
  return (
    <AbsoluteFill>
      <Fondo />
      <div
        style={{
          position: 'absolute',
          left: 960 - TAM_ICONO / 2,
          top: 110,
          opacity: Math.min(1, aparece * 2),
          transform: `scale(${interpolate(aparece, [0, 1], [0.6, 1])})`,
          filter: 'drop-shadow(0 24px 40px rgba(60, 53, 43, 0.18))',
        }}
      >
        <Icono tamano={TAM_ICONO} radio={16} squash={pose.squash} parpado={pose.parpado} />
      </div>
      <Rotulo entra={10} tamano={120} alinear="center" style={{ left: 0, right: 0, top: 480 }}>
        Bito
      </Rotulo>
      <Rotulo entra={30} tamano={64} alinear="center" style={{ left: 0, right: 0, top: 650 }}>
        {texto(lang, 'cierre.titular')}
      </Rotulo>
      <Rotulo entra={50} tamano={32} peso={400} alinear="center" style={{ left: 0, right: 0, top: 790 }}>
        {texto(lang, 'nuevo.cierre.pie')}
      </Rotulo>
      <Rotulo entra={56} tamano={32} peso={600} color={COLOR.hoja} alinear="center" style={{ left: 0, right: 0, top: 845 }}>
        bito.alvarotc.com
      </Rotulo>
    </AbsoluteFill>
  );
};
