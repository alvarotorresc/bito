#!/usr/bin/env node
// Genera F/ui/icons/IconosSistema.kt con los trazados Phosphor Bold 2.1.1 (MIT) de 14-app/sistema/iconos.js.
// Uso: node tools/iconos/phosphor.mjs [ruta de iconos.js] > app/src/main/java/com/alvarotc/bito/ui/icons/IconosSistema.kt
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
const ruta = process.argv[2] || join(process.env.HOME, 'Pictures/bito-habi/direcciones/14-app/sistema/iconos.js');
const s = readFileSync(ruta, 'utf8');
const ICO = (0, eval)('(' + s.slice(s.indexOf('{'), s.indexOf('\n};') + 2) + ')');
const pascal = n => n.split('-').map(p => p[0].toUpperCase() + p.slice(1)).join('');
// ktlint limita las líneas a 140: el trazado se parte en trozos de 96 caracteres unidos con +.
const trozos = d => d.match(/.{1,96}/g).map(t => `"${t}"`).join(' +\n                ');
const lineas = Object.entries(ICO).map(([n, d]) =>
  `    val ${pascal(n)} by lazy {\n        phosphor(\n            "${n}",\n            ${trozos(d)},\n        )\n    }`);
console.log(`// Generado por tools/iconos/phosphor.mjs desde 14-app/sistema/iconos.js. No editar a mano.
// Phosphor Bold 2.1.1 (MIT), https://phosphoricons.com
package com.alvarotc.bito.ui.icons

object IconosSistema {
${lineas.join('\n\n')}
}`);
