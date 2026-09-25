import React from 'react';
import { AbsoluteFill, Html5Audio, Sequence, staticFile } from 'remotion';
import { CUES, DURACION_SONIDO, MUSICA, volumenMusica } from './audio/mezcla';
import type { PromoProps } from './copy';
import { ESCENAS, momentoGlobal, type NumEscena } from './timing';
import { Dedo } from './ui/Dedo';
import { Fondo } from './ui/Fondo';
import { Rotulo } from './ui/Rotulo';

const pendiente = (n: NumEscena): React.FC<PromoProps> => {
  const Pendiente: React.FC<PromoProps> = () => (
    <AbsoluteFill>
      <Fondo />
      <Rotulo tamano={96} alinear="center" style={{ left: 0, right: 0, top: 460 }}>
        {`Escena ${n}`}
      </Rotulo>
      <Dedo x={960} y={760} toque={30} />
    </AbsoluteFill>
  );
  return Pendiente;
};

const ESCENA: Record<NumEscena, React.FC<PromoProps>> = {
  1: pendiente(1),
  2: pendiente(2),
  3: pendiente(3),
  4: pendiente(4),
  5: pendiente(5),
  6: pendiente(6),
  7: pendiente(7),
  8: pendiente(8),
  9: pendiente(9),
};

export const BitoPromo: React.FC<PromoProps> = ({ lang }) => (
  <AbsoluteFill>
    {ESCENAS.map((e) => {
      const Escena = ESCENA[e.n];
      return (
        <Sequence key={e.n} from={e.desde} durationInFrames={e.duracion} name={`${e.n} ${e.nombre}`}>
          <Escena lang={lang} />
        </Sequence>
      );
    })}
    <Html5Audio src={staticFile(MUSICA)} volume={(f) => volumenMusica(f)} name="música" />
    {CUES.map((c) => (
      <Sequence key={`${c.momento}-${c.sonido}`} from={momentoGlobal(c.momento)} durationInFrames={DURACION_SONIDO[c.sonido] + 2} name={`sonido ${c.momento}`}>
        <Html5Audio src={staticFile(`audio/${c.sonido}.wav`)} volume={c.volumen} />
      </Sequence>
    ))}
  </AbsoluteFill>
);
