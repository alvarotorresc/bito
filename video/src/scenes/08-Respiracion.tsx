import React from 'react';
import { AbsoluteFill, useCurrentFrame } from 'remotion';
import { texto, type PromoProps } from '../copy';
import { ALTO, momentoLocal } from '../timing';
import { Dedo } from '../ui/Dedo';
import { Fondo } from '../ui/Fondo';
import { Phone, medidasPhone, pantallaEnFrame, type PasoNavegacion } from '../ui/Phone';
import { Rotulo } from '../ui/Rotulo';
import { TOQUES } from '../ui/shots';

const ALTO_MOVIL = 900;
const m = medidasPhone(ALTO_MOVIL);
const IZQ = 1150;
const ARRIBA = (ALTO - m.altoTotal) / 2;
const TOQUE = TOQUES.hoyARespiracion;
const PASOS: readonly PasoNavegacion[] = [{ pantalla: 'respiracion', at: momentoLocal('toqueRespiracion') + 4 }];

export const Escena08: React.FC<PromoProps> = ({ lang }) => {
  const frame = useCurrentFrame();
  const { pantalla, transicion } = pantallaEnFrame(frame, 'hoy', PASOS);
  return (
    <AbsoluteFill>
      <Fondo />
      <Phone lang={lang} pantalla={pantalla} transicion={transicion} alto={ALTO_MOVIL} style={{ left: IZQ, top: ARRIBA }} />
      <Dedo x={IZQ + m.marco + TOQUE.x * m.ancho} y={ARRIBA + m.marco + TOQUE.y * m.alto} toque={momentoLocal('toqueRespiracion')} />
      <div style={{ position: 'absolute', left: 100, top: 300, width: 900, display: 'flex', flexDirection: 'column', gap: 24 }}>
        <Rotulo entra={10} tamano={64} style={{ position: 'relative' }}>
          {texto(lang, 'nuevo.respiracion.titular')}
        </Rotulo>
        <Rotulo entra={30} tamano={40} peso={400} style={{ position: 'relative' }}>
          {texto(lang, 'nuevo.respiracion.pie')}
        </Rotulo>
      </div>
    </AbsoluteFill>
  );
};
