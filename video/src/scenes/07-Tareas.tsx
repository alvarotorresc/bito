import React from 'react';
import { AbsoluteFill, useCurrentFrame } from 'remotion';
import { texto, type PromoProps } from '../copy';
import { ALTO, momentoLocal } from '../timing';
import { Dedo } from '../ui/Dedo';
import { Fondo } from '../ui/Fondo';
import { Phone, medidasPhone, pantallaEnFrame } from '../ui/Phone';
import { Rotulo } from '../ui/Rotulo';
import { TOQUES } from '../ui/shots';

const ALTO_MOVIL = 900;
const m = medidasPhone(ALTO_MOVIL);
const IZQ = 1150;
const ARRIBA = (ALTO - m.altoTotal) / 2;

const NAVEGA = [
  { pantalla: 'tareas', momento: 'toqueTareas', toque: TOQUES.hoyATareas },
  { pantalla: 'foco', momento: 'toqueFoco', toque: TOQUES.tareasAFoco },
] as const;
const PASOS = NAVEGA.map((n) => ({ pantalla: n.pantalla, at: momentoLocal(n.momento) + 4 }));

export const Escena07: React.FC<PromoProps> = ({ lang }) => {
  const frame = useCurrentFrame();
  const { pantalla, transicion } = pantallaEnFrame(frame, 'hoy', PASOS);
  return (
    <AbsoluteFill>
      <Fondo />
      <Phone lang={lang} pantalla={pantalla} transicion={transicion} alto={ALTO_MOVIL} style={{ left: IZQ, top: ARRIBA }} />
      {NAVEGA.map((n) => (
        <Dedo key={n.momento} x={IZQ + m.marco + n.toque.x * m.ancho} y={ARRIBA + m.marco + n.toque.y * m.alto} toque={momentoLocal(n.momento)} />
      ))}
      <Rotulo entra={10} tamano={64} style={{ left: 100, top: 300, width: 900 }}>
        {texto(lang, 'nuevo.tareas.titular')}
      </Rotulo>
      <Rotulo entra={30} tamano={40} peso={400} style={{ left: 100, top: 470, width: 900 }}>
        {texto(lang, 'nuevo.tareas.pie')}
      </Rotulo>
    </AbsoluteFill>
  );
};
