import React from 'react';
import { AbsoluteFill, interpolate, spring, useCurrentFrame } from 'remotion';
import { texto, type CopyKey, type PromoProps } from '../copy';
import type { LineaHabi } from '../habi/gestos';
import { MUELLES } from '../habi/habiMotion';
import { HabiVivo } from '../habi/HabiVivo';
import { ALTO, ANCHO, FPS, momentoLocal, type Momento } from '../timing';
import { Dedo } from '../ui/Dedo';
import { COLOR, Fondo } from '../ui/Fondo';
import { Phone, medidasPhone } from '../ui/Phone';
import { Rotulo } from '../ui/Rotulo';

const TAM_HABI = 640;
const HABI_IZQ = 60;
const HABI_ARRIBA = 230;
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

type Voz = { nombre: string; frase: CopyKey; def: CopyKey; desde: Momento; hasta: Momento };

const VOCES: readonly Voz[] = [
  { nombre: 'Sargento', frase: 'voz.sargento.frase', def: 'voz.sargento.def', desde: 'caraSargento', hasta: 'caraCheerleader' },
  { nombre: 'Cheerleader', frase: 'voz.cheerleader.frase', def: 'voz.cheerleader.def', desde: 'caraCheerleader', hasta: 'caraNeutra' },
  { nombre: 'Neutra', frase: 'voz.neutra.frase', def: 'voz.neutra.def', desde: 'caraNeutra', hasta: 'tiendaEntra' },
];

const COLUMNA: React.CSSProperties = {
  left: 740,
  width: 1120,
  top: 0,
  height: ALTO,
  display: 'flex',
  flexDirection: 'column',
  justifyContent: 'center',
  alignItems: 'flex-start',
  gap: 36,
};

const BOCADILLO: React.CSSProperties = {
  fontSize: 46,
  fontWeight: 600,
  padding: '30px 40px',
  backgroundColor: COLOR.brillo,
  border: `3px solid ${COLOR.borde}`,
  borderRadius: 36,
};

const PIE: React.CSSProperties = { fontSize: 48, fontWeight: 400, lineHeight: 1.25, marginTop: 8 };

export const Escena06: React.FC<PromoProps> = ({ lang }) => {
  const frame = useCurrentFrame();
  const llega = spring({ frame: Math.max(0, frame - momentoLocal('tiendaEntra')), fps: FPS, config: MUELLES.asentarse });
  const tiendaX = interpolate(llega, [0, 1], [ANCHO + 40, TIENDA_IZQ]);
  const voz = momentoLocal('vozTitular');
  return (
    <AbsoluteFill>
      <Fondo />
      <HabiVivo linea={LINEA_06} tamano={TAM_HABI} style={{ position: 'absolute', left: HABI_IZQ, top: HABI_ARRIBA }} />
      <Dedo x={HABI_IZQ + PUNTO.x * TAM_HABI} y={HABI_ARRIBA + PUNTO.y * TAM_HABI} toque={momentoLocal('toqueHabi')} />
      <Rotulo sale={voz - 8} tamano={104} style={COLUMNA}>
        {texto(lang, 'habi.titular')}
      </Rotulo>
      <Rotulo entra={voz} sale={momentoLocal('caraSargento') - 8} tamano={64} style={COLUMNA}>
        <div>{texto(lang, 'nuevo.habi.voz.titular')}</div>
        <div style={PIE}>{texto(lang, 'habi.nota')}</div>
      </Rotulo>
      {VOCES.map((v) => (
        <Rotulo key={v.nombre} entra={momentoLocal(v.desde) + 6} sale={momentoLocal(v.hasta) - 8} tamano={120} style={COLUMNA}>
          <div>{v.nombre}</div>
          <div style={BOCADILLO}>{texto(lang, v.frase)}</div>
          <div style={PIE}>{texto(lang, v.def)}</div>
        </Rotulo>
      ))}
      {frame >= momentoLocal('tiendaEntra') ? (
        <Phone lang={lang} pantalla="tienda" alto={ALTO_MOVIL} style={{ left: tiendaX, top: (ALTO - m.altoTotal) / 2 }} />
      ) : null}
    </AbsoluteFill>
  );
};
