import React, { useId } from 'react';
import { COLOR } from '../ui/Fondo';

const CUERPO = 'M60,12 C88,12 100,40 100,72 C100,102 82,118 60,118 C38,118 20,102 20,72 C20,40 32,12 60,12 Z';
const OJOS = ['M44,48 m-7,0 a7,7 0 1,1 14,0 a7,7 0 1,1 -14,0', 'M76,48 m-7,0 a7,7 0 1,1 14,0 a7,7 0 1,1 -14,0'];
const BRILLOS = ['M46,45.5 m-2.4,0 a2.4,2.4 0 1,1 4.8,0 a2.4,2.4 0 1,1 -4.8,0', 'M78,45.5 m-2.4,0 a2.4,2.4 0 1,1 4.8,0 a2.4,2.4 0 1,1 -4.8,0'];
const BOCA = 'M50,66 Q60,76 70,66';
const EJE_PARPADO = 48 + 0.55 * 7;

export type IconoProps = { tamano: number; radio?: number; squash?: number; parpado?: number };

export const Icono: React.FC<IconoProps> = ({ tamano, radio = 0, squash = 0, parpado = 0 }) => {
  const clip = `icono-${useId().replace(/[^a-zA-Z0-9_-]/g, '')}`;
  const sx = 1 + 0.1 * squash;
  const sy = 1 - 0.14 * squash;
  const cierre = 1 - 0.96 * Math.min(1, Math.max(0, parpado));
  return (
    <svg width={tamano} height={tamano} viewBox="18 18 72 72">
      <defs>
        <clipPath id={clip}>
          <rect x={18} y={18} width={72} height={72} rx={radio} />
        </clipPath>
      </defs>
      <rect x={18} y={18} width={72} height={72} rx={radio} fill={COLOR.papel} />
      <g clipPath={`url(#${clip})`}>
        <g transform="translate(16.8 37.6) scale(0.62)">
          <g transform={`translate(60 118) scale(${sx} ${sy}) translate(-60 -118)`}>
            <path d={CUERPO} fill={COLOR.salvia} />
            <g transform={`translate(0 ${EJE_PARPADO}) scale(1 ${cierre}) translate(0 ${-EJE_PARPADO})`}>
              {OJOS.map((d) => <path key={d} d={d} fill={COLOR.tinta} />)}
              {BRILLOS.map((d) => <path key={d} d={d} fill={COLOR.brillo} />)}
            </g>
            <path d={BOCA} fill="none" stroke={COLOR.tinta} strokeWidth={6} strokeLinecap="round" />
          </g>
        </g>
      </g>
    </svg>
  );
};

export const IconoStill: React.FC = () => <Icono tamano={1024} />;
