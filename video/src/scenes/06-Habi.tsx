import React from 'react';
import { AbsoluteFill, interpolate, spring, useCurrentFrame } from 'remotion';
import { texto, type PromoProps } from '../copy';
import type { LineaHabi } from '../habi/gestos';
import { MUELLES } from '../habi/habiMotion';
import { HabiVivo } from '../habi/HabiVivo';
import { ALTO, ANCHO, FPS, momentoLocal } from '../timing';
import { Dedo } from '../ui/Dedo';
import { COLOR, Fondo } from '../ui/Fondo';
import { Phone, medidasPhone } from '../ui/Phone';
import { Rotulo } from '../ui/Rotulo';

const TAM_HABI = 620;
const HABI_IZQ = 330;
const HABI_ARRIBA = 330;
const PUNTO = { x: 0.7, y: 0.3 };
const ALTO_MOVIL = 820;
const m = medidasPhone(ALTO_MOVIL);
const TIENDA_IZQ = 1400;

export const LINEA_06: LineaHabi = {
  semilla: 'escena-06',
  eventos: [
    { at: momentoLocal('toqueHabi') - 6, mirada: 'dedo' },
    { at: momentoLocal('toqueHabi'), gesto: { tipo: 'tap', x: PUNTO.x, y: PUNTO.y, salta: true } },
    { at: momentoLocal('toqueHabi') + 24, mirada: 'camara' },
    { at: momentoLocal('caraSargento'), gesto: { tipo: 'swap-face', cara: 'sargento' } },
    { at: momentoLocal('caraCheerleader'), gesto: { tipo: 'swap-face', cara: 'cheerleader' } },
    { at: momentoLocal('caraNeutra'), gesto: { tipo: 'swap-face', cara: 'neutra' } },
    { at: momentoLocal('tiendaEntra'), mirada: 'derecha' },
  ],
};

const BOCADILLOS = [
  { clave: 'voz.sargento', desde: 'caraSargento', hasta: 'caraCheerleader' },
  { clave: 'habi_greeting_cheerleader_radiant', desde: 'caraCheerleader', hasta: 'caraNeutra' },
  { clave: 'habi_sealed_neutra', desde: 'caraNeutra', hasta: 'tiendaEntra' },
] as const;

export const Escena06: React.FC<PromoProps> = ({ lang }) => {
  const frame = useCurrentFrame();
  const llega = spring({ frame: Math.max(0, frame - momentoLocal('tiendaEntra')), fps: FPS, config: MUELLES.asentarse });
  const tiendaX = interpolate(llega, [0, 1], [ANCHO + 40, TIENDA_IZQ]);
  return (
    <AbsoluteFill>
      <Fondo />
      <Rotulo sale={70} tamano={72} style={{ left: 100, top: 110, width: 1000 }}>
        {texto(lang, 'habi.titular')}
      </Rotulo>
      <HabiVivo linea={LINEA_06} tamano={TAM_HABI} style={{ position: 'absolute', left: HABI_IZQ, top: HABI_ARRIBA }} />
      <Dedo x={HABI_IZQ + PUNTO.x * TAM_HABI} y={HABI_ARRIBA + PUNTO.y * TAM_HABI} toque={momentoLocal('toqueHabi')} />
      {BOCADILLOS.map((b) => (
        <Rotulo
          key={b.clave}
          entra={momentoLocal(b.desde) + 6}
          sale={momentoLocal(b.hasta) - 8}
          tamano={52}
          peso={600}
          style={{ left: 1060, top: 380, maxWidth: 720, padding: '36px 48px', backgroundColor: COLOR.brillo, border: `3px solid ${COLOR.borde}`, borderRadius: 36 }}
        >
          {texto(lang, b.clave)}
        </Rotulo>
      ))}
      {frame >= momentoLocal('tiendaEntra') ? (
        <Phone lang={lang} pantalla="tienda" alto={ALTO_MOVIL} style={{ left: tiendaX, top: (ALTO - m.altoTotal) / 2 }} />
      ) : null}
    </AbsoluteFill>
  );
};
