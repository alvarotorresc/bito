import { readFile, readdir } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const FIRMA = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
const MB = 1024 * 1024;

export const CAPTURAS = 8;

export const REGLAS = {
  icono: { ancho: 512, alto: 512, tipoColor: 6, maxBytes: 1 * MB },
  feature: { ancho: 1024, alto: 500, tipoColor: 2, maxBytes: 15 * MB },
  captura: { ancho: 1080, alto: 1920, tipoColor: 2, maxBytes: 8 * MB },
};

export const leerPng = (buf) => {
  if (buf.length < 33 || !buf.subarray(0, 8).equals(FIRMA)) throw new Error('no es un PNG');
  const chunks = [];
  let o = 8;
  while (o + 8 <= buf.length) {
    const largo = buf.readUInt32BE(o);
    const tipo = buf.toString('latin1', o + 4, o + 8);
    chunks.push(tipo);
    o += 12 + largo;
    if (tipo === 'IEND') break;
  }
  return { ancho: buf.readUInt32BE(16), alto: buf.readUInt32BE(20), profundidad: buf[24], tipoColor: buf[25], chunks };
};

export const comprobarPng = (buf, regla) => {
  let png;
  try {
    png = leerPng(buf);
  } catch (e) {
    return [e.message];
  }
  const errores = [];
  if (png.ancho !== regla.ancho || png.alto !== regla.alto) errores.push(`mide ${png.ancho}×${png.alto}, se esperaba ${regla.ancho}×${regla.alto}`);
  if (png.profundidad !== 8) errores.push(`profundidad ${png.profundidad}, se esperaban 8 bits por canal`);
  if (png.tipoColor !== regla.tipoColor) {
    errores.push(`tipo de color ${png.tipoColor}, se esperaba ${regla.tipoColor} (${regla.tipoColor === 2 ? 'RGB 24 bits sin alfa' : 'RGBA 32 bits'})`);
  }
  if (regla.tipoColor === 2 && png.chunks.includes('tRNS')) errores.push('lleva un chunk tRNS (transparencia)');
  if (buf.length > regla.maxBytes) errores.push(`pesa ${buf.length} bytes, el máximo es ${regla.maxBytes}`);
  return errores;
};

export const validarStore = async (dir) => {
  const informe = [];
  const revisar = async (ruta, regla) => {
    let buf;
    try {
      buf = await readFile(ruta);
    } catch {
      informe.push(`${ruta}: no existe`);
      return;
    }
    for (const e of comprobarPng(buf, regla)) informe.push(`${ruta}: ${e}`);
  };
  await revisar(join(dir, 'icon-512.png'), REGLAS.icono);
  for (const lang of ['es', 'en']) {
    await revisar(join(dir, lang, 'feature-graphic.png'), REGLAS.feature);
    let capturas = [];
    try {
      capturas = (await readdir(join(dir, lang))).filter((f) => /^shot-\d\d\.png$/.test(f)).sort();
    } catch {
      capturas = [];
    }
    if (capturas.length !== CAPTURAS) informe.push(`${join(dir, lang)}: ${capturas.length} capturas, se esperaban ${CAPTURAS}`);
    for (const c of capturas) await revisar(join(dir, lang, c), REGLAS.captura);
  }
  return informe;
};

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const dir = process.argv[2] ?? join(dirname(fileURLToPath(import.meta.url)), '..', 'out', 'store');
  const informe = await validarStore(dir);
  if (informe.length > 0) {
    process.stderr.write(`${informe.join('\n')}\n`);
    process.exit(1);
  }
  process.stdout.write(`store válido: ${dir}\n`);
}
