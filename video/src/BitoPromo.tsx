import React from 'react';
import { AbsoluteFill, Html5Audio, Sequence, staticFile } from 'remotion';
import { CUES, DURACION_SONIDO, MUSICA, volumenMusica } from './audio/mezcla';
import type { PromoProps } from './copy';
import { Escena01 } from './scenes/01-Apertura';
import { Escena02 } from './scenes/02-QueEs';
import { Escena03 } from './scenes/03-Registro';
import { Escena04 } from './scenes/04-Triptico';
import { Escena05 } from './scenes/05-Navegacion';
import { Escena06 } from './scenes/06-Habi';
import { Escena07 } from './scenes/07-Tareas';
import { Escena08 } from './scenes/08-Promesas';
import { Escena09 } from './scenes/09-Cierre';
import { ESCENAS, momentoGlobal, type NumEscena } from './timing';

const ESCENA: Record<NumEscena, React.FC<PromoProps>> = {
  1: Escena01,
  2: Escena02,
  3: Escena03,
  4: Escena04,
  5: Escena05,
  6: Escena06,
  7: Escena07,
  8: Escena08,
  9: Escena09,
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
