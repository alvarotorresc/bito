import { describe, expect, it } from 'vitest';
import { en } from '../src/copy/en';
import { es, type CopyKey } from '../src/copy/es';

const VETADOS_ES = [
  'insignia', 'backup', 'entry', 'entries', 'seal', 'badge', 'freezer', 'ledger',
  'serie', 'cadena', 'comodín', 'escudo', 'pase', 'abstinencia',
  'neutral', 'sergeant', 'IA', 'inteligencia artificial',
];
const VETADOS_EN = ['insignia', 'statistics', 'quantity', 'sergeant', 'neutral', 'AI', 'artificial intelligence'];

const patron = (termino: string) => new RegExp(`(?<!\\p{L})${termino}(?:s|es)?(?!\\p{L})`, 'iu');
const claves = Object.keys(es) as CopyKey[];
const largo = (texto: string) => [...texto].length;

describe('copy', () => {
  it('los dos idiomas tienen las mismas claves', () => {
    expect(Object.keys(en).sort()).toEqual([...claves].sort());
  });

  it.each(claves)('%s: el inglés no es más largo que el español', (k) => {
    expect(largo(en[k])).toBeLessThanOrEqual(largo(es[k]));
  });

  it.each(claves)('%s: ningún texto vacío', (k) => {
    expect(es[k].trim().length).toBeGreaterThan(0);
    expect(en[k].trim().length).toBeGreaterThan(0);
  });

  it.each(VETADOS_ES)('el español no dice «%s»', (termino) => {
    for (const k of claves) expect(es[k]).not.toMatch(patron(termino));
  });

  it.each(VETADOS_EN)('el inglés no dice «%s»', (termino) => {
    for (const k of claves) expect(en[k]).not.toMatch(patron(termino));
  });

  it('sin emojis', () => {
    for (const k of claves) {
      expect(es[k]).not.toMatch(/\p{Extended_Pictographic}/u);
      expect(en[k]).not.toMatch(/\p{Extended_Pictographic}/u);
    }
  });

  it('el patrón detecta plurales y tildes sin falsos positivos', () => {
    expect('Tus insignias').toMatch(patron('insignia'));
    expect('sin comodín.').toMatch(patron('comodín'));
    expect('pasear').not.toMatch(patron('pase'));
    expect('Neutra').not.toMatch(patron('neutral'));
  });
});
