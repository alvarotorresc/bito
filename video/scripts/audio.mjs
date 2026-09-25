import { createHash } from 'node:crypto';
import { copyFile, mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const raiz = join(dirname(fileURLToPath(import.meta.url)), '..');
const RAW = join(raiz, '..', 'app', 'src', 'main', 'res', 'raw');
const AUDIO = join(raiz, 'public', 'audio');
const SONIDOS = ['habi_meeh', 'habi_happy', 'habi_cheer', 'habi_sad', 'habi_pop', 'log_tick'];
const ITEM = 'Komikuitstimeforadventure';
const CC0 = 'http://creativecommons.org/publicdomain/zero/1.0/';
const PISTAS = [
  { archivo: 'Komiku_-_03_-_Champ_de_tournesol.mp3', sha1: '841772e6b43634da7f17347e848f00638ef8c752' },
  { archivo: 'Komiku_-_02_-_Le_Grand_Village.mp3', sha1: '90c328e09793d0b4500d99829bbbcc76b5943796' },
  { archivo: 'Komiku_-_04_-_Barque_sur_le_lac.mp3', sha1: '9aee63877979c4cd29cd6bfab1d2b46975189fcf' },
];

const sha1 = (buf) => createHash('sha1').update(buf).digest('hex');

const yaEsta = async (ruta, esperado) => {
  try {
    return sha1(await readFile(ruta)) === esperado;
  } catch {
    return false;
  }
};

await mkdir(join(AUDIO, 'musica'), { recursive: true });
for (const s of SONIDOS) await copyFile(join(RAW, `${s}.wav`), join(AUDIO, `${s}.wav`));

const meta = await (await fetch(`https://archive.org/metadata/${ITEM}`)).json();
if (meta.metadata?.licenseurl !== CC0) throw new Error(`Licencia inesperada en ${ITEM}: ${meta.metadata?.licenseurl}`);
const nodos = [meta.d1, meta.d2, meta.server].filter(Boolean);

for (const p of PISTAS) {
  const destino = join(AUDIO, 'musica', p.archivo);
  if (await yaEsta(destino, p.sha1)) continue;
  let bajada = null;
  for (const nodo of nodos) {
    const r = await fetch(`https://${nodo}${meta.dir}/${encodeURIComponent(p.archivo)}`);
    if (r.ok) {
      bajada = Buffer.from(await r.arrayBuffer());
      break;
    }
  }
  if (!bajada) throw new Error(`Ningún nodo de archive.org sirvió ${p.archivo}`);
  if (sha1(bajada) !== p.sha1) throw new Error(`sha1 distinto en ${p.archivo}: ${sha1(bajada)}`);
  await writeFile(destino, bajada);
}

process.stdout.write(`audio listo: ${SONIDOS.length} sonidos, ${PISTAS.length} pistas CC0\n`);
