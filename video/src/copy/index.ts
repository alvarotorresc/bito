import { en } from './en';
import { es, type CopyKey } from './es';

export type { CopyKey };
export type Lang = 'es' | 'en';
export type PromoProps = { lang: Lang };

export const texto = (lang: Lang, clave: CopyKey): string => (lang === 'es' ? es : en)[clave];
