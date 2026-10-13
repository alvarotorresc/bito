#!/usr/bin/env bash
# Renderiza las recetas de sonido del prototipo (sistema.js) a app/src/main/res/raw/snd_*.ogg.
# Necesita node 22 o posterior, google-chrome (o CHROME=/ruta) y ffmpeg con libvorbis.
# Variables opcionales: SISTEMA_JS (ruta de sistema.js), SALIDA (carpeta de salida), CHROME.
# Volver a ejecutarlo da los mismos archivos byte a byte.
set -euo pipefail
cd "$(dirname "$0")"
command -v node >/dev/null || { echo "Falta node."; exit 1; }
command -v ffmpeg >/dev/null || { echo "Falta ffmpeg."; exit 1; }
ffmpeg -hide_banner -encoders 2>/dev/null | grep -q libvorbis || { echo "ffmpeg no trae libvorbis."; exit 1; }
exec node renderizar.mjs "$@"
