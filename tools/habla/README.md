# Herramientas de textos de Habi

- Borradores: `borradores/<G>.<voz>.tsv` (`bolsa`, `indice`, `etiquetas`, `es`, `en`; G = A-G (G = sesión), voz = sargento, cheerleader o neutra). La tabla de bolsas está en `bolsas.json`.
- `python3 tools/habla/comprobar.py [--sin-minimos] <tsv>…` aplica las reglas de la spec §8.4; una línea por fallo y código 1.
- `python3 tools/habla/muestra.py <tsv>` escribe 30 frases en `muestras/<G>.<voz>.md` (no hace nada si `comprobar` falla).
- `python3 tools/habla/volcar.py --borradores tools/habla/borradores --res app/src/main/res` genera `values{,-es}/strings_habla_<archivo>.xml`.
- Tests: `python3 -m unittest discover -s tools/habla/tests -v`.
- Grupo G (sesión): `borradores/G.<voz>.tsv` se vuelca a `strings_habla_sesion.xml`. Límites de 90 caracteres en globitos y tarjetas del descanso y de 100 en las respuestas de «¿Cómo estás?»; en las bolsas G con `{nombre}` hace falta al menos un item sin él y tantos sin como con él. `muestra.py` rellena `{modo}` con Calmarme/Dormir/Centrarme (Calm/Sleep/Focus).
