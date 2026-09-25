import React from 'react';
import { AbsoluteFill, Easing, interpolate, useCurrentFrame } from 'remotion';
import { texto, type PromoProps } from '../copy';
import type { LineaHabi } from '../habi/gestos';
import { HabiVivo } from '../habi/HabiVivo';
import { ALTO, momentoLocal } from '../timing';
import { Dedo } from '../ui/Dedo';
import { COLOR, Fondo } from '../ui/Fondo';
import { Phone, medidasPhone } from '../ui/Phone';
import { Rotulo, SALIDA } from '../ui/Rotulo';
import { TOQUES } from '../ui/shots';

const CLAMP = { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' } as const;
const ALTO_MOVIL = 820;
const m = medidasPhone(ALTO_MOVIL);
const IZQ = 1100;
const ARRIBA = (ALTO - m.altoTotal) / 2;
const PX = m.marco + TOQUES.hoyRegistro.x * m.ancho;
const PY = m.marco + TOQUES.hoyRegistro.y * m.alto;

export const LINEA_03: LineaHabi = {
  semilla: 'escena-03',
  eventos: [
    { at: 0, mirada: 'derecha' },
    { at: momentoLocal('toqueRegistro') + 2, gesto: { tipo: 'react-log' } },
  ],
};

const Anillo: React.FC<{ x: number; y: number; desde: number }> = ({ x, y, desde }) => {
  const p = interpolate(useCurrentFrame(), [desde, desde + 10], [0, 1], { ...CLAMP, easing: SALIDA });
  if (p === 0) return null;
  const r = 30;
  const c = 2 * Math.PI * r;
  return (
    <svg width={80} height={80} viewBox="0 0 80 80" style={{ position: 'absolute', left: x - 40, top: y - 40 }}>
      <circle cx={40} cy={40} r={r} fill="none" stroke={COLOR.hoja} strokeWidth={6} strokeLinecap="round" strokeDasharray={c} strokeDashoffset={c * (1 - p)} transform="rotate(-90 40 40)" />
    </svg>
  );
};

export const Escena03: React.FC<PromoProps> = ({ lang }) => {
  const frame = useCurrentFrame();
  const zoom = interpolate(frame, [0, 30], [1, 1.7], { ...CLAMP, easing: Easing.inOut(Easing.cubic) });
  const toque = momentoLocal('toqueRegistro');
  return (
    <AbsoluteFill>
      <Fondo />
      <div style={{ position: 'absolute', left: IZQ, top: ARRIBA, width: m.anchoTotal, height: m.altoTotal, transform: `scale(${zoom})`, transformOrigin: `${PX}px ${PY}px` }}>
        <Phone lang={lang} pantalla="hoy" alto={ALTO_MOVIL} style={{ left: 0, top: 0 }} />
      </div>
      <Anillo x={IZQ + PX} y={ARRIBA + PY} desde={toque + 2} />
      <Dedo x={IZQ + PX} y={ARRIBA + PY} toque={toque} />
      <HabiVivo linea={LINEA_03} tamano={300} style={{ position: 'absolute', left: 140, top: 690 }} />
      <Rotulo entra={8} tamano={60} style={{ left: 100, top: 150, width: 720 }}>
        {texto(lang, 'promesa.titular')}
      </Rotulo>
    </AbsoluteFill>
  );
};
