import React from 'react';
import { Img, getStaticFiles, staticFile } from 'remotion';
import type { Lang } from '../copy';
import { COLOR } from './Fondo';
import { FUENTE } from './Rotulo';
import { resolverShot, type Pantalla } from './shots';

export const PANTALLA_ANCHO = 1080;
export const PANTALLA_ALTO = 2340;

export const medidasPhone = (alto: number) => {
  const ancho = (alto * PANTALLA_ANCHO) / PANTALLA_ALTO;
  const marco = alto * 0.022;
  return { ancho, alto, marco, anchoTotal: ancho + 2 * marco, altoTotal: alto + 2 * marco, radio: alto * 0.055 };
};

export type Transicion = { desde: Pantalla; progreso: number };

type PhoneProps = { lang: Lang; pantalla: Pantalla; alto: number; transicion?: Transicion; style?: React.CSSProperties };

export const Phone: React.FC<PhoneProps> = ({ lang, pantalla, alto, transicion, style }) => {
  const m = medidasPhone(alto);
  const disponibles = getStaticFiles().map((f) => f.name);

  const capa = (p: Pantalla, desplazamiento: number) => {
    const ruta = resolverShot(disponibles, lang, p);
    return (
      <div key={p} style={{ position: 'absolute', inset: 0, transform: `translateX(${desplazamiento}%)` }}>
        {ruta ? (
          <Img src={staticFile(ruta)} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
        ) : (
          <div
            style={{
              width: '100%',
              height: '100%',
              backgroundColor: COLOR.placeholder,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontFamily: FUENTE,
              fontSize: alto * 0.03,
              color: COLOR.tinta,
            }}
          >
            {p}
          </div>
        )}
      </div>
    );
  };

  const cambiando = transicion !== undefined && transicion.progreso < 1 && transicion.desde !== pantalla;

  return (
    <div
      style={{
        position: 'absolute',
        boxSizing: 'border-box',
        width: m.anchoTotal,
        height: m.altoTotal,
        padding: m.marco,
        borderRadius: m.radio + m.marco,
        backgroundColor: COLOR.tinta,
        boxShadow: '0 40px 80px rgba(60, 53, 43, 0.22)',
        ...style,
      }}
    >
      <div style={{ position: 'relative', width: m.ancho, height: m.alto, borderRadius: m.radio, overflow: 'hidden', backgroundColor: COLOR.placeholder }}>
        {cambiando ? capa(transicion.desde, -30 * transicion.progreso) : null}
        {capa(pantalla, cambiando ? 100 * (1 - transicion.progreso) : 0)}
      </div>
    </div>
  );
};
