# Vídeo de producto de Bito

Vídeo promocional de 64 s (1920×1080, 30 fps) en español e inglés y el material gráfico de
Google Play, hechos con Remotion 4.0.529. Todo sale de este directorio; la app no se toca.

## Requisitos

- Node 22 y npm. `ffmpeg` y `ffprobe` en el `PATH` para verificar el vídeo y convertir los PNG.
- Solo para las capturas: Android SDK, JDK 21, `/dev/kvm` y Maestro. El detalle está en
  `shots/README.md`.

## Flujo

```bash
npm install        # una vez por checkout o worktree
npm run audio      # copia los WAV de la app y baja la música CC0 (red solo la primera vez)
npm run shots      # emulador headless, unos 11 minutos; ver shots/README.md
npm run final      # tests → comprobación de capturas → render es/en → verificar → store
```

`npm run audio` comprueba el sha1 de cada pista. Si las tres ya están, no sale a la red, así que
`npm run final` funciona sin conexión después de la primera vez.

`npm run final` encadena:

1. `vitest run`: tiempos, parpadeos, toques, mezcla de audio, copia y validador de PNG.
2. `scripts/comprobar-shots.mjs`: corta el render si falta alguna de las 26 capturas en
   `public/shots/{es,en}/`. Nunca sale un MP4 con placeholders grises.
3. `render:es` y `render:en`: H.264 CRF 18 con audio AAC.
4. `verificar`: `scripts/verificar-video.sh` mide resolución, fps, duración y pista de audio.
5. `store`: stills y PNG de Play, validados por `scripts/validar-store.mjs` (sin canal alfa
   salvo el icono de 512).

Para iterar sin capturas reales: `npm run studio` o `npm run render:borrador`.

## Qué queda en `out/`

- `bito-promo-es.mp4` y `bito-promo-en.mp4`.
- `stills/`: icono de 1024, ilustración y fotograma promocional por idioma.
- `store/icon-512.png` y `store/{es,en}/`: feature graphic (1024×500) y seis capturas de
  1080×1920 por idioma, listas para subir a Play.
- `bundle/` y `tmp/`: intermedios.

`out/` está en `.gitignore` y no se sube nunca: se regenera con `npm run final`.

## Imágenes de la web

La web (alvarotc-web) no usa `out/`. Sus imágenes salen de las capturas crudas con
`python3 shots/to_web.py`, que sigue el brief `docs/MEDIA_PROYECTOS.md` de la web. Por defecto
escribe en el checkout de alvarotc-web; `--web RUTA` cambia el destino.

## Textos

- `src/copy/es.ts` y `src/copy/en.ts`. Las claves son los ID del mensaje maestro
  (`docs/08-mensaje-maestro.md`); las claves nuevas llevan el prefijo `nuevo.`.
- Cada texto inglés tiene igual o menos caracteres que su español. Lo comprueba
  `test/copy.test.ts`. Si un inglés se pasa, se reescribe el inglés, nunca el español.
- Sin emojis ni menciones a IA, con el vocabulario del glosario del mensaje maestro.

## Pendiente del autor

Las divergencias entre el spec y el mensaje maestro y las claves `nuevo.` por validar están en la
tabla «Pendiente del autor» de `docs/superpowers/plans/2026-09-25-video-remotion.md`.
