import React from 'react';

const TINTA = '#3C352B';
const R_OJO = 0.0456;

export const sargento = {
  ejeParpado: 0.5 + 0.55 * R_OJO,
  mofletes: (
    <g stroke={TINTA} strokeOpacity={0.95} strokeWidth={0.03} strokeLinecap="round" fill="none">
      <line x1={0.317713832} y1={0.584609094} x2={0.402286168} y2={0.615390906} />
      <line x1={0.597713832} y1={0.615390906} x2={0.682286168} y2={0.584609094} />
    </g>
  ),
  ojos: (
    <>
      <circle cx={0.36} cy={0.5} r={R_OJO} fill={TINTA} />
      <circle cx={0.64} cy={0.5} r={R_OJO} fill={TINTA} />
    </>
  ),
  brillos: null,
  cejas: (
    <g stroke={TINTA} strokeWidth={0.026} strokeLinecap="round" fill="none">
      <line x1={0.302936609} y1={0.40145898} x2={0.417063391} y2={0.43854102} />
      <line x1={0.582936609} y1={0.43854102} x2={0.697063391} y2={0.40145898} />
    </g>
  ),
  boca: (
    <path
      d="M 0.47 0.66 Q 0.54 0.68125 0.61 0.66"
      transform="rotate(6 0.54 0.66)"
      fill="none"
      stroke={TINTA}
      strokeWidth={0.028}
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  ),
};
