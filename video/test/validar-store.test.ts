import { mkdtempSync, rmSync, writeFileSync, mkdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterAll, describe, expect, it } from 'vitest';
import { REGLAS, comprobarPng, leerPng, validarStore } from '../scripts/validar-store.mjs';

const FIRMA = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
const chunk = (tipo: string, datos: Buffer) => {
  const b = Buffer.alloc(12 + datos.length);
  b.writeUInt32BE(datos.length, 0);
  b.write(tipo, 4, 'latin1');
  datos.copy(b, 8);
  return b;
};
const png = (ancho: number, alto: number, tipoColor: number, extra: string[] = []) => {
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(ancho, 0);
  ihdr.writeUInt32BE(alto, 4);
  ihdr[8] = 8;
  ihdr[9] = tipoColor;
  return Buffer.concat([FIRMA, chunk('IHDR', ihdr), ...extra.map((t) => chunk(t, Buffer.alloc(1))), chunk('IEND', Buffer.alloc(0))]);
};

describe('leerPng', () => {
  it('lee medidas, profundidad, tipo de color y chunks', () => {
    expect(leerPng(png(1080, 1920, 2, ['tRNS']))).toEqual({ ancho: 1080, alto: 1920, profundidad: 8, tipoColor: 2, chunks: ['IHDR', 'tRNS', 'IEND'] });
  });
});

describe('comprobarPng', () => {
  it('acepta una captura RGB de 1080×1920', () => expect(comprobarPng(png(1080, 1920, 2), REGLAS.captura)).toEqual([]));
  it('rechaza una captura con alfa', () => expect(comprobarPng(png(1080, 1920, 6), REGLAS.captura).join()).toMatch(/tipo de color 6/));
  it('rechaza una captura RGB con tRNS', () => expect(comprobarPng(png(1080, 1920, 2, ['tRNS']), REGLAS.captura).join()).toMatch(/tRNS/));
  it('rechaza una medida distinta', () => expect(comprobarPng(png(1080, 2340, 2), REGLAS.captura).join()).toMatch(/mide 1080×2340/));
  it('acepta el feature graphic RGB de 1024×500', () => expect(comprobarPng(png(1024, 500, 2), REGLAS.feature)).toEqual([]));
  it('acepta el icono RGBA de 512', () => expect(comprobarPng(png(512, 512, 6), REGLAS.icono)).toEqual([]));
  it('rechaza el icono sin alfa', () => expect(comprobarPng(png(512, 512, 2), REGLAS.icono).join()).toMatch(/tipo de color 2/));
  it('rechaza lo que no es PNG', () => expect(comprobarPng(Buffer.from('hola'), REGLAS.icono)).toEqual(['no es un PNG']));
});

describe('validarStore', () => {
  const dir = mkdtempSync(join(tmpdir(), 'store-'));
  afterAll(() => rmSync(dir, { recursive: true, force: true }));

  it('da por bueno un store completo y detecta una captura que falta', async () => {
    writeFileSync(join(dir, 'icon-512.png'), png(512, 512, 6));
    for (const lang of ['es', 'en']) {
      mkdirSync(join(dir, lang), { recursive: true });
      writeFileSync(join(dir, lang, 'feature-graphic.png'), png(1024, 500, 2));
      for (let i = 1; i <= 6; i++) writeFileSync(join(dir, lang, `shot-0${i}.png`), png(1080, 1920, 2));
    }
    expect(await validarStore(dir)).toEqual([]);
    rmSync(join(dir, 'en', 'shot-06.png'));
    expect((await validarStore(dir)).join()).toMatch(/5 capturas, se esperaban 6/);
  });
});
