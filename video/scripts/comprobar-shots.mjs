import { existsSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const raiz = join(dirname(fileURLToPath(import.meta.url)), '..');
const fuente = readFileSync(join(raiz, 'src', 'ui', 'shots.ts'), 'utf8');
const bloque = fuente.match(/export const PANTALLAS = \[([^\]]+)\]/);
if (!bloque) throw new Error('No encuentro PANTALLAS en src/ui/shots.ts');
const pantallas = [...bloque[1].matchAll(/'([a-z-]+)'/g)].map((m) => m[1]);

const faltan = ['es', 'en']
  .flatMap((lang) => pantallas.map((p) => `public/shots/${lang}/${p}.png`))
  .filter((ruta) => !existsSync(join(raiz, ruta)));

if (faltan.length > 0) {
  process.stderr.write(`Faltan ${faltan.length} capturas; el render final no sale con placeholders:\n${faltan.join('\n')}\n`);
  process.exit(1);
}
process.stdout.write(`${pantallas.length * 2} capturas presentes\n`);
