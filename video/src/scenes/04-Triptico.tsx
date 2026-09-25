import React from 'react';
import { AbsoluteFill, interpolate, spring, useCurrentFrame } from 'remotion';
import { texto, type PromoProps } from '../copy';
import type { LineaHabi } from '../habi/gestos';
import { MUELLES } from '../habi/habiMotion';
import { HabiVivo } from '../habi/HabiVivo';
import { FPS, momentoLocal } from '../timing';
import { Dedo } from '../ui/Dedo';
import { Fondo } from '../ui/Fondo';
import { Phone, medidasPhone } from '../ui/Phone';
import { Rotulo } from '../ui/Rotulo';
import { TOQUES } from '../ui/shots';

const CLAMP = { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' } as const;
const ALTO_MOVIL = 620;
const m = medidasPhone(ALTO_MOVIL);
const ARRIBA = 170;

const PANELES = [
  { pantalla: 'widget', pie: 'promesa.widget', momento: 'tickWidget', toque: TOQUES.widget, cx: 400 },
  { pantalla: 'notificacion', pie: 'promesa.notificacion', momento: 'tickNotificacion', toque: TOQUES.notificacion, cx: 960 },
  { pantalla: 'repaso', pie: 'promesa.repaso', momento: 'tickRepaso', toque: TOQUES.repaso, cx: 1520 },
] as const;

export const LINEA_04: LineaHabi = {
  semilla: 'escena-04',
  eventos: [
    { at: momentoLocal('tickWidget') - 8, mirada: 'izquierda' },
    { at: momentoLocal('tickNotificacion') - 8, mirada: 'arriba' },
    { at: momentoLocal('tickRepaso') - 8, mirada: 'derecha' },
  ],
};

export const Escena04: React.FC<PromoProps> = ({ lang }) => {
  const frame = useCurrentFrame();
  const primeraLuz = momentoLocal(PANELES[0].momento) - 10;
  return (
    <AbsoluteFill>
      <Fondo />
      <Rotulo tamano={46} alinear="center" style={{ left: 160, width: 1600, top: 60 }}>
        {texto(lang, 'promesa.subtitulo')}
      </Rotulo>
      {PANELES.map((panel, i) => {
        const s = spring({ frame: Math.max(0, frame - i * 8), fps: FPS, config: MUELLES.asentarse });
        const mom = momentoLocal(panel.momento);
        const siguiente = PANELES[i + 1];
        const enciende = interpolate(frame, [mom - 10, mom - 2], [0, 1], CLAMP);
        const apaga = siguiente ? interpolate(frame, [momentoLocal(siguiente.momento) - 10, momentoLocal(siguiente.momento) - 2], [1, 0], CLAMP) : 1;
        const luz = enciende * apaga;
        const opacidad = frame < primeraLuz ? 1 : 0.55 + 0.45 * luz;
        const izq = panel.cx - m.anchoTotal / 2;
        return (
          <React.Fragment key={panel.pantalla}>
            <Phone
              lang={lang}
              pantalla={panel.pantalla}
              alto={ALTO_MOVIL}
              style={{ left: izq, top: ARRIBA + (1 - s) * 80, opacity: opacidad * Math.min(1, s), transform: `scale(${1 + 0.05 * luz})` }}
            />
            <Dedo x={izq + m.marco + panel.toque.x * m.ancho} y={ARRIBA + m.marco + panel.toque.y * m.alto} toque={mom} />
            <Rotulo entra={i * 8 + 6} tamano={34} peso={600} alinear="center" style={{ left: panel.cx - 240, width: 480, top: ARRIBA + m.altoTotal + 24 }}>
              {texto(lang, panel.pie)}
            </Rotulo>
          </React.Fragment>
        );
      })}
      <HabiVivo linea={LINEA_04} tamano={170} style={{ position: 'absolute', left: 875, top: 860 }} />
    </AbsoluteFill>
  );
};
