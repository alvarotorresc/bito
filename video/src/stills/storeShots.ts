import type { CopyKey } from '../copy';
import type { Pantalla } from '../ui/shots';

export const STORE_SHOTS: readonly { pantalla: Pantalla; titular: CopyKey }[] = [
  { pantalla: 'hoy', titular: 'frase' },
  { pantalla: 'detalle', titular: 'hace.rachas' },
  { pantalla: 'notificacion', titular: 'promesa.notificacion' },
  { pantalla: 'widget', titular: 'promesa.widget' },
  { pantalla: 'repaso', titular: 'promesa.repaso' },
  { pantalla: 'foco', titular: 'store.foco' },
  { pantalla: 'respiracion', titular: 'store.respiracion' },
  { pantalla: 'logros', titular: 'hace.logros' },
];
