import React from 'react';
import { AbsoluteFill } from 'remotion';
import { texto, type PromoProps } from '../copy';
import { COLOR, Fondo } from '../ui/Fondo';
import { Rotulo } from '../ui/Rotulo';

const PROMESAS = ['nunca.1', 'nunca.2', 'nunca.3'] as const;
const CIFRAS = [
  { numero: '0', pie: 'cifra.gms' },
  { numero: '4', pie: 'cifra.permisos' },
  { numero: '100%', pie: 'cifra.licencia' },
] as const;

export const Escena09: React.FC<PromoProps> = ({ lang }) => (
  <AbsoluteFill>
    <Fondo tono="oscuro" />
    <Rotulo tamano={68} color={COLOR.papel} alinear="center" style={{ left: 0, right: 0, top: 110 }}>
      {texto(lang, 'nunca.titular')}
    </Rotulo>
    {PROMESAS.map((clave, i) => (
      <Rotulo key={clave} entra={20 + i * 12} tamano={48} peso={400} color={COLOR.papel} alinear="center" style={{ left: 0, right: 0, top: 260 + i * 80 }}>
        <span style={{ color: COLOR.salvia, fontWeight: 700 }}>{texto(lang, 'nunca.palabra')}</span> {texto(lang, clave)}
      </Rotulo>
    ))}
    {CIFRAS.map((cifra, i) => (
      <React.Fragment key={cifra.pie}>
        <Rotulo entra={80 + i * 10} tamano={120} color={COLOR.salvia} alinear="center" style={{ left: 160 + i * 540, width: 520, top: 610 }}>
          {lang === 'es' ? cifra.numero.replace('%', '\u202F%') : cifra.numero}
        </Rotulo>
        <Rotulo entra={84 + i * 10} tamano={30} peso={400} color="rgba(242, 236, 225, 0.85)" alinear="center" style={{ left: 140 + i * 540, width: 560, top: 770 }}>
          {texto(lang, cifra.pie)}
        </Rotulo>
      </React.Fragment>
    ))}
  </AbsoluteFill>
);
