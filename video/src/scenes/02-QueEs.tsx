import React from 'react';
import { AbsoluteFill, interpolate, spring, useCurrentFrame } from 'remotion';
import { texto, type PromoProps } from '../copy';
import type { LineaHabi } from '../habi/gestos';
import { MUELLES } from '../habi/habiMotion';
import { HabiVivo } from '../habi/HabiVivo';
import { ALTO, ANCHO, FPS, momentoLocal } from '../timing';
import { COLOR, Fondo } from '../ui/Fondo';
import { Phone, medidasPhone } from '../ui/Phone';
import { Rotulo } from '../ui/Rotulo';

const ALTO_MOVIL = 860;
const m = medidasPhone(ALTO_MOVIL);
const MOVIL_IZQ = 1840 - m.anchoTotal;

export const FINAL_02 = {
  movil: { izq: MOVIL_IZQ, arriba: (ALTO - m.altoTotal) / 2, altoTotal: m.altoTotal },
  habi: { tamano: 420, izq: 90, arriba: 520 },
};

export const LINEA_02: LineaHabi = { semilla: 'escena-02', eventos: [{ at: momentoLocal('miraMovil'), mirada: 'derecha' }] };

export const Escena02: React.FC<PromoProps> = ({ lang }) => {
  const frame = useCurrentFrame();
  const llega = spring({ frame: Math.max(0, frame - momentoLocal('movilEntra')), fps: FPS, config: MUELLES.asentarse });
  const x = interpolate(llega, [0, 1], [ANCHO + 40, MOVIL_IZQ]);
  const frases = texto(lang, 'descriptor.2').split(/(?<=\.)\s+/);
  return (
    <AbsoluteFill>
      <Fondo />
      <HabiVivo linea={LINEA_02} tamano={FINAL_02.habi.tamano} style={{ position: 'absolute', left: FINAL_02.habi.izq, top: FINAL_02.habi.arriba }} />
      <Rotulo entra={6} tamano={60} style={{ left: 560, top: 250, width: 680 }}>
        {texto(lang, 'descriptor.1')}
      </Rotulo>
      {frases.map((frase, i) => (
        <Rotulo key={frase} entra={momentoLocal('rotuloSin') + i * 12} tamano={40} peso={600} color={COLOR.hoja} style={{ left: 560, top: 520 + i * 64, width: 680 }}>
          {frase}
        </Rotulo>
      ))}
      <Phone lang={lang} pantalla="hoy" alto={ALTO_MOVIL} style={{ left: x, top: FINAL_02.movil.arriba }} />
    </AbsoluteFill>
  );
};
