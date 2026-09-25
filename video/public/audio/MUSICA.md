# Música del vídeo

Pista en uso: `musica/Komiku_-_03_-_Champ_de_tournesol.mp3` (constante `MUSICA` en `src/audio/mezcla.ts`).

Todas son de Komiku, álbum «It's time for adventure», con dedicación CC0 1.0: dominio público, sin atribución obligatoria.
Verificación: https://archive.org/details/Komikuitstimeforadventure (en la metadata del ítem, `licenseurl` = http://creativecommons.org/publicdomain/zero/1.0/).
`npm run audio` descarga las tres y comprueba la licencia y el sha1 antes de escribirlas.

| Pista | Duración | sha1 |
| --- | --- | --- |
| Komiku_-_03_-_Champ_de_tournesol.mp3 (en uso) | 118 s | 841772e6b43634da7f17347e848f00638ef8c752 |
| Komiku_-_02_-_Le_Grand_Village.mp3 (alternativa 1) | 104 s | 90c328e09793d0b4500d99829bbbcc76b5943796 |
| Komiku_-_04_-_Barque_sur_le_lac.mp3 (alternativa 2) | 206 s | 9aee63877979c4cd29cd6bfab1d2b46975189fcf |

Para cambiar de pista, edita solo `MUSICA` en `src/audio/mezcla.ts`. La elige el autor entre las tres.

Los WAV (`habi_*.wav`, `log_tick.wav`) son copias sin tocar de `app/src/main/res/raw/`, con la misma licencia GPL que la app.
