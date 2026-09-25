import React from 'react';

const TINTA = '#3C352B';
const BRILLO = '#FBF8F2';
const R_OJO = 0.048;

export const neutra = {
  ejeParpado: 0.5 + 0.55 * R_OJO,
  mofletes: null,
  ojos: (
    <>
      <circle cx={0.36} cy={0.5} r={R_OJO} fill={TINTA} />
      <circle cx={0.64} cy={0.5} r={R_OJO} fill={TINTA} />
    </>
  ),
  brillos: (
    <>
      <circle cx={0.3864} cy={0.464} r={0.01296} fill={BRILLO} />
      <circle cx={0.6664} cy={0.464} r={0.01296} fill={BRILLO} />
    </>
  ),
  cejas: null,
  boca: (
    <path
      d="M 0.428 0.66 Q 0.5 0.68975 0.572 0.66"
      fill="none"
      stroke={TINTA}
      strokeWidth={0.028}
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  ),
};
