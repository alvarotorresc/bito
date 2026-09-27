import { execFileSync } from 'node:child_process';
import { mkdirSync, rmSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { CAPTURAS } from './validar-store.mjs';

const raiz = join(dirname(fileURLToPath(import.meta.url)), '..');

const ejecutar = (cmd, args) => execFileSync(cmd, args, { cwd: raiz, stdio: 'inherit' });
const still = (comp, salida, props) =>
  ejecutar('npx', ['remotion', 'still', 'out/bundle', comp, salida, ...(props ? [`--props=${JSON.stringify(props)}`] : [])]);
const convertir = (entrada, salida, formato, escala) =>
  ejecutar('ffmpeg', ['-y', '-loglevel', 'error', '-i', entrada, ...(escala ? ['-vf', `scale=${escala}:flags=lanczos`] : []), '-pix_fmt', formato, salida]);

rmSync(join(raiz, 'out', 'store'), { recursive: true, force: true });
for (const d of ['out/tmp', 'out/stills', 'out/store/es', 'out/store/en']) mkdirSync(join(raiz, d), { recursive: true });

ejecutar('npx', ['remotion', 'bundle', 'src/index.ts', '--out-dir', 'out/bundle']);

still('Icon', 'out/tmp/icon.png');
convertir('out/tmp/icon.png', 'out/stills/icon-1024.png', 'rgb24');
convertir('out/tmp/icon.png', 'out/store/icon-512.png', 'rgba', '512:512');
still('Ilustracion', 'out/tmp/ilustracion.png');
convertir('out/tmp/ilustracion.png', 'out/stills/illustration.png', 'rgb24');

for (const lang of ['es', 'en']) {
  still('Promo', `out/tmp/promo-${lang}.png`, { lang });
  convertir(`out/tmp/promo-${lang}.png`, `out/stills/promo-${lang}.png`, 'rgb24');
  still('FeatureGraphic', `out/tmp/fg-${lang}.png`, { lang });
  convertir(`out/tmp/fg-${lang}.png`, `out/store/${lang}/feature-graphic.png`, 'rgb24');
  for (let i = 0; i < CAPTURAS; i++) {
    const n = String(i + 1).padStart(2, '0');
    still('StoreShot', `out/tmp/shot-${lang}-${n}.png`, { lang, indice: i });
    convertir(`out/tmp/shot-${lang}-${n}.png`, `out/store/${lang}/shot-${n}.png`, 'rgb24');
  }
}

ejecutar('node', ['scripts/validar-store.mjs']);
