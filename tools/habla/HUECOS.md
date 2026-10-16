# Huecos de los textos de Habi

Qué significa cada hueco, en qué bolsa y con qué formato llega. Manda sobre los borradores: un trozo que lo contradiga se corrige. La fuente es la spec de la pieza 4 (§5, §8.1 y §8.2). Lo que la spec no fija está marcado **decisión de coherencia**.

## Reglas generales

- **Un hueco ausente no se rellena con nada.** El redactor solo elige trozos cuyos huecos estén todos en la candidata (§4.4). Si un dato puede faltar (no hay lo gordo, la tarea no tiene primer paso, el hábito no es de minutos), el motor no pasa ese hueco y salen los trozos que no lo usan. Por eso cada bolsa necesita, en cada voz, trozos que se entiendan sin los huecos opcionales.
- **Texto libre** (`{habito}`, `{tarea}`, `{primer_paso}`, elementos de `{gordo}` y `{lista}`): el nombre lo escribe la persona. Ver §8.3 regla 4: tras «: », entre comillas («…» en ES, “…” en EN) o al principio de la frase. Nunca concuerda con nada: ni artículo delante («la {habito}») ni adjetivo o participio detrás. Mal: «{habito}» hecho. Bien: «{habito}»: hecho.
- **Plurales en línea** `{x|singular|plural}` solo cuando la unidad es conocida (días fuera, días limpios, cosas, tareas, días perfectos, bitos). Imprimen el número y la palabra.
- **Números que pueden ser 1:** si el valor puede ser 1, el texto no puede presuponer plural: ni verbo en plural concertado con él («Faltan {faltan}», «Pasan a mañana {n|…}»), ni pronombre plural que lo recoja («resuélvelas»).
- **Unidad desconocida:** las rachas cuentan **periodos** del hábito (día, semana o mes, `Habit.Period`). El texto no sabe cuál es (decisión de coherencia, ver `{racha}`).

## Tabla de huecos

### `{nombre}`
Nombre de la persona (ajustes). Como mucho una vez por frase. Solo en las bolsas con `nombre` en `bolsas.json`. Si no hay nombre, el motor no lo pasa: no hay apodo de repuesto (§5.13).

### `{habito}`
Nombre del hábito tal como lo escribió la persona. Texto libre. Qué hábito es:

| Bolsa | Hábito |
|---|---|
| `nu_ayer_escapo` | el que se escapó ayer y está pendiente hoy |
| `nu_vuelta`, `nu_dia_duro` | el de mejor cumplimiento en 30 días (§5.1) |
| `nu_regular` | la candidata de menor objetivo (§5.5) |
| `nu_racha_cerca` | el de la racha a 1 o 2 del hito |
| `nu_dia_flojo` | el que flojea hoy, día de la semana |
| `nu_hora_habitual` | el pendiente cuya hora habitual se acerca |
| `fi_*` | el de la ficha (casi siempre sobra: la nota está dentro de la ficha) |
| `re_vuelta` | el de la racha que empezó tras la vuelta |
| `re_racha_recuperada` | el de la racha recuperada |
| `re_cumpliste_hora` | el del aviso aceptado y cumplido ayer |
| `cie_logro` | el que ayer se escapó y hoy se hizo |
| `cie_racha_avanza` | el de la racha que llega a un múltiplo de 5 |

### `{tarea}`
Nombre de una tarea. Texto libre.

| Bolsa | Tarea |
|---|---|
| `nu_elegir_gordo` | la sugerencia de Habi (§5.4: lo gordo de ayer sin hacer, la vencida más antigua o la que vence hoy). Sin sugerencia, no hay `{tarea}` |
| `nu_tarea_vence` | la tarea vencida o que vence hoy |
| `nu_noche`, `av_noche_cuerpo` | la primera tarea de lo gordo que quedó sin hacer: «Mañana empezamos por…» (§5.4, arrastre). Sin arrastre, no hay `{tarea}` |
| `re_gordo_hecho` | la (primera) tarea de lo gordo hecha ayer |
| `nu_gordo_sin_empezar` | **decisión de coherencia:** no se pasa. La situación aporta `{gordo}` y `{primer_paso}` |

### `{gordo}`
Lo gordo de hoy: **lista de 1 a 3 nombres** (tareas o hábitos), unida con «, » y « y » («, » y « and » en EN). El texto no presupone cuántos son: nada de «es tuya», «la tarea», «las tres». Se nombra como «lo gordo» y el hueco va tras «: » o al principio. No se pone entre comillas (una lista de tres entre comillas parece un único nombre).

| Bolsa | Qué trae |
|---|---|
| `nu_gordo_sin_empezar`, `nu_gordo_elegido` | lo gordo elegido |
| `nu_confirma_vale_gordo`, `av_aceptado_cuerpo`, `av_aceptado_titulo` | lo gordo **sin hacer**. Es «mientras tanto»: la hora aceptada **no es de lo gordo** (ver `{hora}`) |
| `cie_gordo_hecho` | lo gordo entero. **Decisión de coherencia:** `CIE_GORDO_HECHO` sale solo si todo lo gordo está hecho; con una parte hecha, el cierre pasa a la siguiente prioridad |
| `av_manana_titulo_gordo` | lo gordo si ya está elegido; si no, la sugerencia de Habi (una tarea). **Decisión de coherencia:** se nombra igual, «lo gordo», porque la hoja abre con la sugerencia marcada (§5.4, D13). El texto no dice «elegiste» |

### `{primer_paso}`
El «primer paso» (`Task.firstStep`) de la primera tarea de lo gordo que lo tenga. Texto libre: tras «: », entre comillas o al principio. Si ninguna tarea de lo gordo tiene primer paso, no se pasa.

### `{hora}`
Hora local con el formato del sistema (ES 24 h «14:30»; EN «2:30 PM» o «14:30»). En ES va como «a las {hora}» o «las {hora}» (nunca cae a la 1: va al menos 30 min por delante y antes de la noche, §5.3). En EN, «at {hora}» o «{hora}» suelto.

| Bolsa | Qué hora |
|---|---|
| `nu_ayer_escapo`, `fi_hoy_falta` | la hora propuesta (§5.3). Si no hay hora posible, no se pasa |
| `nu_hora_habitual` | la hora habitual del hábito |
| `nu_confirma_vale`, `nu_confirma_vale_gordo` | la hora que acabas de aceptar para el **hábito** |
| `av_aceptado_titulo`, `av_aceptado_cuerpo` | la hora aceptada para el **hábito** de la propuesta. Nunca la hora de lo gordo: lo gordo no tiene «Vale, a las…» (§5.1). Mal: «Lo gordo, a las {hora}», «{gordo}, a las {hora}», «Lo gordo ya tiene hora». Bien: «Te aviso a las {hora}. Mientras, lo gordo: {gordo}.» |
| `re_cumpliste_hora` | el minuto aceptado ayer (`ACEPTADO_CUMPLIDO`) |

**Cuándo falta `{hora}`.** Es opcional (no está en `obligatorios`) en estas bolsas, y cada voz necesita trozos que se entiendan sin ella:

- `nu_ayer_escapo`: falta si no hay hora posible (§5.3) o si ya hay dos avisos aceptados hoy: el motor la quita y el «Vale, a las…» pasa a «Ahora» (§5.2).
- `nu_hora_habitual`: hoy el motor la pasa siempre (es la hora habitual, no una propuesta). Sigue siendo opcional en el contrato.
- `nu_dia_flojo` y `nu_racha_cerca`: estas bolsas no tienen el hueco `{hora}`. La hora propuesta solo va en el botón «Vale, a las…», y con dos avisos aceptados el botón pasa a «Ahora».
- `fi_hoy_falta`: la nota solo sale con hora posible, y con dos avisos aceptados el motor no la saca (§5.2). En la práctica llega con hora, pero el contrato no lo exige.
- `nu_confirma_vale`: falta tras un «Vale» sin hora (el día duro, o con dos avisos ya aceptados). La confirmación sale igual (§5.2), sin botones: no hay aviso programado, así que los trozos sin `{hora}` no prometen avisar.

En `nu_confirma_vale_gordo`, `{hora}` es obligatorio: un «Vale» sin hora confirma siempre con `nu_confirma_vale`, aunque quede lo gordo sin hacer.

**Decisión de coherencia:** `av_aceptado_cuerpo` se envía **siempre**, haya o no lo gordo; `{gordo}` solo se pasa si queda lo gordo sin hacer. Cada voz necesita trozos sin `{gordo}` que se entiendan solos.

### `{minutos}`
`nu_ayer_escapo`: el objetivo del hábito en minutos, solo si es de duración (≥ 1). En otros tipos no se pasa. Se escribe siempre «{minutos} min» (abreviatura invariable, ES y EN).

### `{racha}`, `{mejor}` y `{faltan}`
Números enteros de **periodos** del hábito (días, semanas o meses: la ficha ya los dice así, `ficha_semanas_seguidas`). **Decisión de coherencia:** el texto no conoce la unidad, así que:

- **Prohibido:** `{racha|día|días}`, «{racha} días», «días seguidos», «un día más», «cada día de esta racha», «la semana seguida».
- **Permitido:** el número suelto («racha de {racha}», «la racha va por {racha}», «Récord: {mejor}», «streak of {racha}», «{racha} in a row»), o la forma neutra `{racha|vez|veces} seguidas` / `{racha|time|times} in a row`. En ES no se usa «{racha} seguidos» suelto (concordaría con «días»).

| Hueco | Bolsa | Valor |
|---|---|---|
| `{racha}` | `nu_racha_cerca` | racha actual, a 1 o 2 de 7, 30, 100 o 365 (≥ 5) |
| `{racha}` | `fi_racha_vs_mejor` | racha actual (≥ 3) |
| `{mejor}` | `fi_racha_vs_mejor` | mejor racha de siempre. **Puede ser igual a `{racha}`** (la ficha ya lo trata así): el texto no presupone que el récord esté por encima |
| `{racha}` | `cie_racha_avanza` | la racha de `{habito}` que hoy llega a un múltiplo de 5 |
| `{racha}` | `re_vuelta` | la racha de `{habito}` empezada tras la vuelta (≥ 7) |
| `{racha}` | `re_racha_recuperada` | la racha recuperada de `{habito}` (> 7) |
| `{faltan}` | `nu_racha_cerca` | periodos que faltan para el hito: **1 o 2**. En ES nunca como sujeto de un verbo («Faltan 1»): «a {faltan} del hito», «el hito queda a {faltan}» |

### `{bitos}`
`nu_racha_cerca`: bitos del hito (5, 20, 75 o 300, `EconomyConfig`). Siempre plural: vale «{bitos} bitos» o `{bitos|bito|bitos}`. En EN la moneda también es «bitos», nunca «bits».

### `{dias_fuera}`
`nu_vuelta`: días lógicos sin actividad (≥ 3). Unidad conocida, días: vale «{dias_fuera} días» o `{dias_fuera|día|días}`.

### `{dia_semana}`
Día de la semana. ES en minúscula («lunes», «sábado»); EN con mayúscula («Monday»).

| Bolsa | Qué día |
|---|---|
| `nu_dia_flojo`, `nu_vuelta`, `re_patron_animo` | hoy |
| `fi_mejor_dia` | el día de la semana con mejor cumplimiento (no hoy) |

- **ES, solo singular:** «el {dia_semana}», «cada {dia_semana}», «este {dia_semana}», «un {dia_semana}», «es {dia_semana}». **Prohibido «los {dia_semana}»:** «los sábado» y «los domingo» fallan (lunes a viernes no cambian en plural, sábado y domingo sí). Tampoco abre frase en ES (saldría en minúscula).
- **EN:** vale el singular y el plural «{dia_semana}s» (los siete días forman el plural con «s»). Puede abrir frase.

### `{pct}`
Porcentaje entero. Siempre se escribe «{pct} %» (§8.2).

| Bolsa | Valor | Signo |
|---|---|---|
| `fi_constancia`, `fi_objetivo` | cumplimiento de los últimos 30 días, 0–100 | sin signo. «de los días» no: el hábito puede ser semanal |
| `fi_mejor_dia` | cumplimiento de ese día de la semana, 0–100 | sin signo |
| `ca_*` | cambio frente a la semana pasada | **con signo ya puesto**: «+12», «+0», «−5». El texto no añade signo ni presupone si sube o baja |

### `{objetivo}`
`fi_objetivo`: el objetivo sugerido (la mitad, como mínimo el paso) **ya formateado con su unidad**, como lo muestra la ficha («15 min», «4 vasos»). **Decisión de coherencia:** el texto no añade unidad.

### `{n}`

| Bolsa | Qué es | ¿Puede ser 0 o 1? |
|---|---|---|
| `fi_limpio` | días limpios (dejar del todo) | ≥ 1; unidad días: `{n|día limpio|días limpios}` |
| `ca_*` | días perfectos de este mes | **Decisión de coherencia:** con 0 el motor no lo pasa; ≥ 1: `{n|día perfecto|días perfectos}` |
| `nu_cerrar_dia` | filas por decidir en el cierre | ≥ 1: `{n|cosa|cosas}`; sin verbo ni pronombre en plural concertado |
| `cie_tareas_manana` | **total** de tareas que pasan a mañana (las nombradas y las de «N más») | ≥ 1: `{n|tarea|tareas}`; sin verbo en plural concertado |
| `nu_todo_hecho`, `nu_noche`, `nu_tras_hoy_no` | la situación no lo aporta | no se usa |
| `re_*` | la situación no lo aporta (decisión de coherencia) | no se usa |

### `{lista}`
`cie_tareas_manana`: tareas de hoy sin hacer, como mucho 2 nombres y, si hay más, «N más» como último elemento: «Informe, Llamar al banco y 3 más» / «Report, Call the bank and 3 more». **Ya trae el «y N más»**: el texto no añade nada detrás. Puede ser una sola tarea: nada de «te esperan», «pasan» o «quedan» en plural concertado con la lista; vale «Para mañana: {lista}», «Lo pendiente se queda para mañana: {lista}».

### `{hechos}` y `{total}`
«Tu día» N de M: hábitos de hoy hechos y exigibles. **Decisión de coherencia:** con `total` = 0 el motor no los pasa. Bolsas: `nu_saludo`, `av_recordatorio`, `av_noche_titulo`, `av_noche_cuerpo` (y `av_manana_titulo*`, sin uso hoy).

### `{fecha}`
`fi_pausa`: desde cuándo está en pausa. **Decisión de coherencia:** ES «3 de octubre» sin artículo y sin año, y el texto escribe «desde el {fecha}»; EN «October 3», «since {fecha}».
