# Capturas de la app para el vídeo

Pasada reproducible de 26 capturas (13 pantallas × es/en) de Bito 1.1.0 real en un emulador
headless. Salida en `../public/shots/{es,en}/*.png`, 1080×2340 RGB, sin barra de estado.

Pantallas: `hoy, detalle, stats, records, logros, habi, tienda, repaso, ajustes, tareas,
widget, notificacion, foco`.

## Requisitos

- Android SDK en `~/Android/Sdk` con `cmdline-tools/latest`. `emulator.sh setup` instala
  `emulator`, `platform-tools` y `system-images;android-35;default;x86_64`.
- `/dev/kvm`, JDK 21 en `~/.jdks/jdk-21.0.12+8`, Maestro en `~/.maestro/bin/maestro`, Python 3
  con Pillow e ImageMagick para revisar.

## Uso

```bash
bash emulator.sh setup      # una vez: instala, crea el AVD bito-shots y mide las barras
bash build_apk.sh           # en cada release: assembleDebug 1.1.0
npm run shots               # desde video/: pasada completa es+en y verificación
python3 to_web.py           # copia a alvarotc-web lo que pide su brief de medios
```

Una sola pantalla: `python3 run_shots.py --lang es --screens stats`, y después
`python3 verify_shots.py`. La pasada completa tarda unos 11 minutos (678 s medidos); `notificacion` espera a que
salte un recordatorio real (hasta 4 minutos por idioma).

`verify_shots.py` comprueba que existen las 26, que miden 1080×2340 RGB, que la fila superior es
Papel (`#F2ECE1`) en las pantallas de `PAPEL_SCREENS` y que cada par es/en no es idéntico. Sale
con 1 si hay algún problema.

## Geometría

El AVD mide 1080×2406 a densidad 440, con navegación por gestos (`avd.env`: `TOP=66`,
`BOTTOM=0`, `HEIGHT=2406`, medidos con `dumpsys window`). `crop.py` recorta `1080x2340+0+66`:
quita la barra de estado y **conserva** la zona de gestos de abajo, con su asa fina. No se
recorta abajo porque la app es edge-to-edge y su barra de navegación en pastilla se extiende
bajo la zona de gestos: un recorte inferior la cortaba. Es lo que se ve en un móvil real.

Excepción: `notificacion` (`CROP_FROM_TOP` en `run_shots.py`) se recorta desde arriba,
`1080x2340+0+0`. Conserva la cabecera de la persiana (hora, fecha, ajustes rápidos) y pierde los
66 px de la zona de gestos, que en la persiana no tienen nada. Durante esa captura se apagan el
wifi y el aviso de depuración USB (`adb_notify 0`), y el resto de notificaciones que no son de
Bito (la de «consola en serie habilitada») se posponen 10 minutos con `cmd notification snooze`.
El AVD se crea con `hw.keyboard=no` para que no salga «Teclados físicos configurados».

En `foco`, Habi parpadea: `run_shots.py` mira si hay píxeles oscuros en la zona de sus ojos y,
si no, repite la captura cada 400 ms (hasta 5 veces); si no los encuentra, el intento falla.

Las pantallas `widget`, `notificacion`, `foco` y `tienda` no pasan la comprobación de Papel
(pantalla de inicio, persiana, foco a pantalla completa y tienda: se baja hasta el final y se sube un poco, así que la tarjeta de la tienda queda arriba del todo).

## Idioma

`emulator.sh locale es|en` cambia el locale del sistema: `adb root` + `setprop
persist.sys.locale` + reinicio del framework y, si no funciona, arranque en frío con
`-change-locale`. Se verifica con `am get-config`. Cada ejecución imprime cuál usó
(`locale: es-ES por …`). Después se aplica el modo demo de SystemUI: 9:41, batería llena, sin
iconos de notificación, wifi ni datos móviles. El primer `am start` tras cambiar el locale se
reintenta (10 veces cada 3 s) porque el framework puede no estar listo todavía.

## Datos de demo

`prepare.py` desinstala, instala con permisos, abre la app una vez para que cree su base y su
DataStore, las baja con `run-as`, siembra con `seed.py` (Lucía, seis hábitos, 130 días de racha
en agua, corona, cuatro tareas, hoy a medias) y escribe los ajustes con `prefs_pb.py`. Los
avisos de logros y de día perfecto quedan marcados como vistos y no se programa ningún
recordatorio durante la pasada, salvo el de `notificacion` (`prepare.py --arm-reminder 2`).
`streak-100` sale desbloqueado a propósito: lo exige la racha de 130 días y la corona.

## Widget y notificación (best-effort)

Se intentan dos veces cada una. Si no salen, `run_shots.py` usa `fallback/<lang>/<pantalla>.png`
si existe y, si no, las deja como `missing` y `verify_shots.py` falla.

El flujo `flows/widget.yaml` usa toques por coordenadas donde el launcher no expone nodos:
pulsación larga en `80%,62%` de la pantalla de inicio para abrir el menú (Widgets), y en el
selector de widgets el botón de añadir en `50%,84%` (se repite una vez si no aparece la pantalla
de configuración). Si cambia la imagen del sistema o la resolución del AVD, hay que revisar esos
dos puntos con `maestro --device emulator-5554 hierarchy` y una captura cruda de `raw/`.

Otros desplazamientos fijos, calibrados en este AVD (1080×2406, densidad 440):

- `flows/logros.yaml`: un swipe lento de `50%,80%` a `50%,50%` (1500 ms). Deja arriba «Rachas»
  y enteras las tarjetas de Rachas y Constancia, con «Semana redonda» a la vista. Uno más corto
  cortaba por arriba la tarjeta «9 de 14» y fallaba la comprobación de Papel.
- `flows/tienda.yaml`: cuatro swipes rápidos hasta el final de la pantalla de Habi, que siempre
  dejan la misma posición, y uno lento hacia arriba. En en va de `50%,50%` a `50%,54%` (unos 75 px
  de desplazamiento). En es va de `540,1203` a `540,1311` (unos 87 px), porque su rejilla es unos
  47 px más alta: «Rosa empolvado» ocupa dos líneas. En es, el título y las pestañas quedan
  enteros y el borde de arriba de la tarjeta queda justo fuera. `run_shots.py`
  (`buy_button_whole`) comprueba en la columna x=300 que debajo del botón de comprar se ven al
  menos 20 px de su tarjeta; si no, el intento falla.

Fallback con fotos del autor:

1. En el móvil del autor, con Bito 1.1.0, el idioma del sistema correspondiente y los datos de
   demo, hacer una captura de pantalla nativa del widget en la pantalla de inicio y otra de la
   notificación con la persiana abierta.
2. Normalizar a 1080×2340 RGB y guardarla en `fallback/es/` o `fallback/en/` con el nombre de la
   pantalla. Pixel 9a (1080×2424, barra de estado arriba):
   - widget, sin la barra de estado:
     `magick entrada.png -resize 1080x -gravity north -crop 1080x2340+0+<alto barra estado> +repage -alpha off fallback/es/widget.png`
   - notificación, desde arriba como en el emulador:
     `magick entrada.png -resize 1080x -gravity north -crop 1080x2340+0+0 +repage -alpha off fallback/es/notificacion.png`
3. Comprobar con `magick identify fallback/es/widget.png` que da `1080x2340` y volver a lanzar
   `python3 run_shots.py --lang es --screens widget` y `python3 verify_shots.py`.

Hoy las dos salen en el emulador y `fallback/` no existe.

## Plan B de siembra

Si `run-as` no deja escribir, se puede generar una copia de seguridad de esquema 3 con los
mismos datos y restaurarla a mano desde Ajustes: es la Tarea 15 del plan
`docs/superpowers/plans/2026-09-25-video-capturas.md`. No hizo falta: `run-as` funciona con el
APK debug, así que `backup_json.py` no existe.
