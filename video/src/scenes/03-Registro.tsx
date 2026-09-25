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
import { FINAL_02 } from './02-QueEs';
import { TOQUES } from '../ui/shots';

const CLAMP = { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' } as const;
const ALTO_MOVIL = 820;
const m = medidasPhone(ALTO_MOVIL);
const IZQ = 1100;
const ARRIBA = (ALTO - m.altoTotal) / 2;
const PX = m.marco + TOQUES.hoyRegistro.x * m.ancho;
const PY = m.marco + TOQUES.hoyRegistro.y * m.alto;
const ZOOM = 1.7;
const ESCALA_0 = FINAL_02.movil.altoTotal / m.altoTotal;
const HABI = { tamano: 300, izq: 140, arriba: 690 };

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
  const p = interpolate(frame, [0, 30], [0, 1], { ...CLAMP, easing: Easing.inOut(Easing.cubic) });
  const mezcla = (a: number, b: number) => a + (b - a) * p;
  const escala = mezcla(ESCALA_0, ZOOM);
  const tx = mezcla(FINAL_02.movil.izq - IZQ, PX * (1 - ZOOM));
  const ty = mezcla(FINAL_02.movil.arriba - ARRIBA, PY * (1 - ZOOM));
  const habi = FINAL_02.habi;
  const toque = momentoLocal('toqueRegistro');
  return (
    <AbsoluteFill>
      <Fondo />
      <div style={{ position: 'absolute', left: IZQ, top: ARRIBA, width: m.anchoTotal, height: m.altoTotal, transform: `translate(${tx}px, ${ty}px) scale(${escala})`, transformOrigin: '0 0' }}>
        <Phone lang={lang} pantalla="hoy" alto={ALTO_MOVIL} style={{ left: 0, top: 0 }} />
      </div>
      <Anillo x={IZQ + PX} y={ARRIBA + PY} desde={toque + 2} />
      <Dedo x={IZQ + PX} y={ARRIBA + PY} toque={toque} />
      <HabiVivo linea={LINEA_03} tamano={mezcla(habi.tamano, HABI.tamano)} style={{ position: 'absolute', left: mezcla(habi.izq, HABI.izq), top: mezcla(habi.arriba, HABI.arriba) }} />
      <Rotulo entra={8} tamano={60} style={{ left: 100, top: 150, width: 720 }}>
        {texto(lang, 'promesa.titular')}
      </Rotulo>
    </AbsoluteFill>
  );
};
