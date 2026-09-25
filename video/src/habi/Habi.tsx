import React, { useId } from 'react';
import { cheerleader } from './faces/cheerleader';
import { neutra } from './faces/neutra';
import { sargento } from './faces/sargento';
import { BREATH_LIFT, BREATH_SCALE, GAZE_X, GAZE_Y, SALTO_ALTURA, SQUASH_X, SQUASH_Y, sombra } from './habiMotion';

type CaraDef = {
  ejeParpado: number;
  mofletes: React.ReactNode | null;
  ojos: React.ReactNode;
  brillos: React.ReactNode | null;
  cejas: React.ReactNode | null;
  boca: React.ReactNode;
};

export const CARAS: Record<'neutra' | 'sargento' | 'cheerleader', CaraDef> = { neutra, sargento, cheerleader };
export type Cara = keyof typeof CARAS;

export type HabiPose = {
  respira: number;
  parpado: number;
  miradaX: number;
  miradaY: number;
  squash: number;
  salto: number;
  inclinacion: number;
  desplazY: number;
  cara: Cara;
  caraPrevia: Cara;
  mezclaCara: number;
};

export const POSE_REPOSO: HabiPose = {
  respira: 0,
  parpado: 0,
  miradaX: 0,
  miradaY: 0,
  squash: 0,
  salto: 0,
  inclinacion: 0,
  desplazY: 0,
  cara: 'neutra',
  caraPrevia: 'neutra',
  mezclaCara: 1,
};

const CUERPO =
  'M 0.5 0.13 C 0.687776815 0.13 0.84 0.340426168 0.84 0.6 C 0.84 0.804345357 0.687776815 0.97 0.5 0.97 C 0.312223185 0.97 0.16 0.804345357 0.16 0.6 C 0.16 0.340426168 0.312223185 0.13 0.5 0.13 Z';
const SALVIA = '#A9C9A1';
const TINTE_SOMBREADO = '#768D71';
const BRILLO = '#FBF8F2';
const SUELO = '#E7DFD1';

const acota = (v: number, min: number, max: number) => Math.min(max, Math.max(min, v));

const CapaCara: React.FC<{ def: CaraDef; opacidad: number; mirada: string; cierre: number }> = ({ def, opacidad, mirada, cierre }) => (
  <g data-parte="cara" opacity={acota(opacidad, 0, 1)}>
    {def.mofletes ? <g data-parte="mofletes">{def.mofletes}</g> : null}
    <g data-parte="ojos" transform={mirada}>
      <g transform={`translate(0.5 ${def.ejeParpado}) scale(1 ${cierre}) translate(-0.5 ${-def.ejeParpado})`}>
        {def.ojos}
        {def.brillos ? <g data-parte="brillos">{def.brillos}</g> : null}
      </g>
    </g>
    {def.cejas ? <g data-parte="cejas">{def.cejas}</g> : null}
    <g data-parte="boca">{def.boca}</g>
  </g>
);

export const Habi: React.FC<{ pose: HabiPose; tamano: number; style?: React.CSSProperties }> = ({ pose, tamano, style }) => {
  const id = useId().replace(/[^a-zA-Z0-9_-]/g, '');
  const clip = `habi-clip-${id}`;
  const sombreado = `habi-sombreado-${id}`;
  const sx = (1 + SQUASH_X * pose.squash) * (1 - BREATH_SCALE * 0.5 * pose.respira);
  const sy = (1 - SQUASH_Y * pose.squash) * (1 + BREATH_SCALE * pose.respira);
  const dy = pose.desplazY - BREATH_LIFT * pose.respira - SALTO_ALTURA * pose.salto;
  const cuerpo = `translate(0.5 ${1 + dy}) rotate(${pose.inclinacion}) scale(${sx} ${sy}) translate(-0.5 -1)`;
  const s = sombra(pose);
  const mirada = `translate(${GAZE_X * acota(pose.miradaX, -1, 1)} ${GAZE_Y * acota(pose.miradaY, -1, 1)})`;
  const cierre = 1 - 0.96 * acota(pose.parpado, 0, 1);
  const cambiando = pose.caraPrevia !== pose.cara && pose.mezclaCara < 1;

  return (
    <svg width={tamano} height={tamano} viewBox="0 0 1 1" overflow="visible" style={style}>
      <defs>
        <clipPath id={clip}>
          <path d={CUERPO} />
        </clipPath>
        <linearGradient id={sombreado} gradientUnits="userSpaceOnUse" x1={0} y1={0.78} x2={0} y2={0.97}>
          <stop offset={0} stopColor={TINTE_SOMBREADO} stopOpacity={0} />
          <stop offset={1} stopColor={TINTE_SOMBREADO} stopOpacity={0.11} />
        </linearGradient>
      </defs>
      <g data-parte="sombra" transform={`translate(0.5 0.94) scale(${s.escalaX} 1)`} opacity={s.opacidad}>
        <ellipse cx={0} cy={0} rx={0.307} ry={0.073} fill={SUELO} fillOpacity={0.6} />
      </g>
      <g data-parte="cuerpo" transform={cuerpo}>
        <path d={CUERPO} fill={SALVIA} />
        <g clipPath={`url(#${clip})`}>
          <rect x={0.16} y={0.78} width={0.68} height={0.19} fill={`url(#${sombreado})`} />
          <ellipse cx={0.5} cy={0.16} rx={0.05} ry={0.035} fill={BRILLO} fillOpacity={0.8} />
        </g>
        {cambiando ? <CapaCara def={CARAS[pose.caraPrevia]} opacidad={1 - pose.mezclaCara} mirada={mirada} cierre={cierre} /> : null}
        <CapaCara def={CARAS[pose.cara]} opacidad={cambiando ? pose.mezclaCara : 1} mirada={mirada} cierre={cierre} />
      </g>
    </svg>
  );
};
