import React from 'react';
import { AbsoluteFill, useCurrentFrame } from 'remotion';
import { texto, type PromoProps } from '../copy';
import type { LineaHabi } from '../habi/gestos';
import { HabiVivo } from '../habi/HabiVivo';
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
  { pantalla: 'detalle', momento: 'toqueDetalle', toque: TOQUES.hoyADetalle },
  { pantalla: 'stats', momento: 'toqueStats', toque: TOQUES.detalleAStats },
  { pantalla: 'logros', momento: 'toqueLogros', toque: TOQUES.statsALogros },
] as const;
const PASOS = NAVEGA.map((n) => ({ pantalla: n.pantalla, at: momentoLocal(n.momento) + 4 }));

export const LINEA_05: LineaHabi = {
  semilla: 'escena-05',
  animoInicial: 'radiante',
  eventos: [
    { at: 0, mirada: 'derecha' },
    { at: momentoLocal('celebracion') - 6, mirada: 'camara' },
    { at: momentoLocal('celebracion'), gesto: { tipo: 'celebrate' } },
  ],
};

export const Escena05: React.FC<PromoProps> = ({ lang }) => {
  const frame = useCurrentFrame();
  const { pantalla, transicion } = pantallaEnFrame(frame, 'hoy', PASOS);
  return (
    <AbsoluteFill>
      <Fondo />
      <Phone lang={lang} pantalla={pantalla} transicion={transicion} alto={ALTO_MOVIL} style={{ left: IZQ, top: ARRIBA }} />
      {NAVEGA.map((n) => (
        <Dedo key={n.momento} x={IZQ + m.marco + n.toque.x * m.ancho} y={ARRIBA + m.marco + n.toque.y * m.alto} toque={momentoLocal(n.momento)} />
      ))}
      <HabiVivo linea={LINEA_05} tamano={400} style={{ position: 'absolute', left: 250, top: 560 }} />
      <Rotulo entra={momentoLocal('toqueDetalle') + 5} sale={momentoLocal('toqueStats') - 5} tamano={72} style={{ left: 100, top: 200, width: 900 }}>
        {texto(lang, 'hace.rachas')}
      </Rotulo>
      <Rotulo entra={momentoLocal('toqueStats') + 5} tamano={72} style={{ left: 100, top: 200, width: 900 }}>
        {texto(lang, 'hace.logros')}
      </Rotulo>
    </AbsoluteFill>
  );
};
