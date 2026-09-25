import React from 'react';

const TINTA = '#3C352B';
const BRILLO = '#FBF8F2';
const MOFLETE = '#E8B4A8';
const R_OJO = 0.0552;

export const cheerleader = {
  ejeParpado: 0.5 + 0.55 * R_OJO,
  mofletes: (
    <>
      <circle cx={0.3} cy={0.615} r={0.05} fill={MOFLETE} fillOpacity={0.69} />
      <circle cx={0.7} cy={0.615} r={0.05} fill={MOFLETE} fillOpacity={0.69} />
    </>
  ),
  ojos: (
    <>
      <circle cx={0.36} cy={0.5} r={R_OJO} fill={TINTA} />
      <circle cx={0.64} cy={0.5} r={R_OJO} fill={TINTA} />
    </>
  ),
  brillos: (
    <>
      <circle cx={0.39036} cy={0.4586} r={0.014904} fill={BRILLO} />
      <circle cx={0.41244} cy={0.49172} r={0.008556} fill={BRILLO} />
      <circle cx={0.67036} cy={0.4586} r={0.014904} fill={BRILLO} />
      <circle cx={0.69244} cy={0.49172} r={0.008556} fill={BRILLO} />
    </>
  ),
  cejas: null,
  boca: <path d="M 0.4122 0.66 Q 0.5 0.75 0.5878 0.66 Q 0.5 0.6402 0.4122 0.66 Z" fill={TINTA} />,
};
