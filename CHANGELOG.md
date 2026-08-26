# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/). Versionado [SemVer](https://semver.org/lang/es/).

## [1.0.0] - 2026-08-26

La primera versión para compartir. Bito ya se instala en el móvil de cualquiera.

### Added

- **Acerca de** en Ajustes: la versión, el código fuente, la licencia libre explicada, y un formulario de un minuto para contar lo que falle — sin cuentas ni registros.
- **Widget de un solo hábito**: eliges cuál al colocarlo y ves su progreso de hoy; un toque lo registra. El widget de siempre ahora también cabe pequeño.
- **Las notificaciones hablan como Habi**: cada personalidad avisa a su manera y te dice lo que te queda del día. El tono cambia con la hora — por la mañana plantea el día, por la tarde cuenta cómo vas, por la noche te ayuda a cerrarlo. En una instalación nueva vienen dos avisos puestos de serie, editables o borrables cuando quieras.
- **Sonido y vibración al registrar**, cada uno con su interruptor en Ajustes.
- **Racha de cariños**: tres caricias seguidas y Habi se derrite de gusto, con corazones incluidos.
- **La tabla de puntos**: el saldo de la tienda abre una hoja que explica cómo se gana cada punto.
- **Volver a ver la introducción** desde Ajustes, sin tocar nada de lo tuyo.
- **Usar un congelador** se explica desde el detalle del hábito, en la voz de tu personalidad.

### Changed

- **Habi está vivo**: se hunde como gelatina justo donde lo tocas, te sigue con la mirada, respira al ritmo de su ánimo y nunca reacciona dos veces igual. Su voz sale toda de un maullido de verdad, y los estampados de la tienda se curvan sobre su cuerpo en vez de quedarse pegados encima.
- **El mismo Habi en todas las pantallas**, con lo que lleve puesto.
- **Ajustes**, ordenado por secciones con su título, sus iconos y sus separadores; el fin del día se muda a General y los recordatorios se gestionan en su propia hoja.
- **Esa jerarquía de texto se extiende a toda la app**: lo importante en negrita, lo secundario en un tono más suave.
- **Crear un hábito**: cada tipo estrena icono, las opciones se ordenan en rejilla y Habi te acompaña desde su bocadillo.
- **La introducción**, fiel al diseño hasta el último píxel en sus tres escenas de historia.
- **Pausar y archivar** ya no van de verde: el verde queda para lo que suma.
- **Cerrar el día** es instantáneo, y el botón pasa a «Día sellado».
- Los días pasados sin cumplir se ven en rojo en los calendarios, y cada casilla del mes lleva su número. Los días de descanso de un hábito semanal no se marcan.
- Stats y Habi ya no tardan en aparecer al cambiar de pestaña.
- La barra inferior reparte sus pestañas a partes iguales, en español y en inglés.

### Fixed

- El widget se actualiza al toque: lo que registras dentro de la app se ve fuera al instante.
- Deshacer se aparta con el dedo y no reaparece al volver a Hoy.
- La vibración al registrar obedece a tu interruptor, no al ajuste general del sistema.
- Comprar en la tienda ya no es a ciegas: al elegir un accesorio, Habi vuelve a escena para que lo veas puesto.

## [0.9.5] - 2026-08-23

### Added

- Accesibilidad con TalkBack en toda la app (la deuda pendiente desde la v0.3.0): las acciones rápidas de Hoy dicen qué hacen («Marcar Meditar como hecho», «Sumar 1 a Agua»), y escribir un valor exacto ya no vive solo detrás de una pulsación larga: aparece como acción etiquetada. La barra inferior anuncia qué pestaña está activa. El calendario de puntos del Detalle y de Stats se lee como un resumen del mes y cada día dice su estado. Los anillos y barras de progreso son barras de progreso de verdad. Las tarjetas (récords, números, logros, filas del repaso, ítems de la tienda de Habi) se leen como una sola unidad. Las píldoras de selección (períodos, presets, personalidad, ejes de la tienda, chips del onboarding) anuncian «seleccionado». Los pasos del onboarding se anuncian («Paso 2 de 6»). Habi se presenta con su ánimo. Las filas con interruptor de Ajustes y del formulario se activan tocando en cualquier punto de la fila y se anuncian con su estado. Los iconos de los steppers dicen «Más»/«Menos». Español e inglés.

### Changed

- Un restaurador con historial ya no repite el onboarding: si restauras un backup con hábitos en una instalación limpia, la app arranca en Hoy (tu nombre viaja en el backup desde la v0.1.0).
- Restaurar un backup aplica también su idioma al momento, y el chip de idioma de la bienvenida refleja el idioma real igual que Ajustes.
- Un aviso tocado durante el onboarding ya no te saca del flujo: espera y se abre al llegar a Hoy.
- Pasar de página con el dedo en el onboarding ya no repite el fundido de entrada (solo parpadeaba).
- El objetivo semanal se limita a 7 en el modelo, no solo en la pantalla.
- Si configuras la firma por variables de entorno y falta alguna, el build lo dice con nombre y apellido.

### Fixed

- Una carrera al marcar los logros como vistos podía perder la marca si dos pantallas la escribían a la vez.
- La copia de seguridad: el estado «necesita contraseña» reacciona también cuando se repara la clave local; menos idas y venidas al proveedor de archivos; un temporal que no se puede borrar tras un fallo ya no se silencia.
- Los tests: un arranque idempotente con scope inyectable mata el fallo intermitente de la suite completa que arrastrábamos desde la v0.8.0, y cubre el cableado real del arranque.

## [0.9.0] - 2026-08-22

### Added

- Bito por fin te pregunta tu nombre: un onboarding con la historia de Habi — bienvenida con elección de idioma, la historia en tres escenas (saltables), tu nombre con reacción de Habi en vivo, la elección de personalidad escuchando su voz, y tu primer hábito con la sugerencia del widget. En instalaciones ya rodadas sale una vez tras actualizar, sin tocar tus datos: aprovecha para ponerte nombre.
- Las tres personalidades estrenan sus textos definitivos en español e inglés, en todos los rincones donde Habi habla: saludos, comentarios, repaso, logros, congeladores, notificaciones y el formulario de crear hábito.
- Elegir idioma de verdad: selector en Ajustes (del sistema, español o inglés) que se aplica al momento y sobrevive reinicios en cualquier Android.
- Tu nombre, editable en Ajustes cuando quieras.
- Micro-animaciones por todas partes: el anillo de Hoy crece hasta su progreso, el punto que registras hace pop, las tarjetas se reacomodan suaves y las pantallas se deslizan al navegar. Las celebraciones siguen siendo las únicas con fanfarria, como debe ser.

### Changed

- El detalle de un hábito sin registros te invita a estrenar el primer día en vez de mostrar solo ceros.
- «Ajustes» ya no se parte en dos líneas en la barra inferior.
- El anillo de Hoy se anuncia como barra de progreso para lectores de pantalla.

### Fixed

- Rendimiento: cada registro recalculaba dos veces el historial completo de días perfectos; ahora una sola.

## [0.8.0] - 2026-08-21

### Added

- Backup automático: eliges una carpeta (vale una que sincronices con tu nube) y Bito guarda solo una copia diaria o semanal, conserva las últimas que le digas y borra las más antiguas. La escritura es atómica: un corte a medias jamás deja una copia rota. Nada sale del móvil salvo que tú sincronices la carpeta.
- Cifrado opcional de backups con contraseña (Argon2id + AES-256-GCM). La contraseña no se guarda en ningún sitio y sin ella la copia es irrecuperable — la app lo avisa bien claro antes de activarlo. Para restaurar en otro móvil solo hace falta la contraseña.
- La sección de Backups de Ajustes, rediseñada: el estado del último backup con cuántas copias hay en la carpeta, hacer backup ahora, restaurar y exportar, todo en un sitio.
- Las versiones taggeadas publican solas una release firmada con su APK en GitHub.

### Changed

- Sellar el día retira la notificación del repaso de la bandeja, la selles desde donde la selles.

### Fixed

- Restaurar un backup de versiones viejas ya no celebra de golpe todos los logros históricos al abrir la app.
- Exportar a mano encima de un archivo más grande dejaba restos en algunos gestores de archivos y corrompía la copia; ahora se trunca siempre.

## [0.7.0] - 2026-08-20

### Added

- Repaso del día: una pantalla para cerrar la jornada con solo lo que queda por hacer — marcar hecho o dejarlo pasar sin culpa, sumar lo que falte, confirmar el día limpio o apuntar una recaída — y sellar el día. Al sellar, Habi aparece en su escenario con el anillo final, los puntos ganados hoy y las rachas que avanzan. Si llevas días sin abrir la app, el repaso ofrece sellarlos de golpe.
- «Cerrar el día» desde la tarjeta del anillo en Hoy; la notificación del repaso abre el repaso directamente.
- Logros: catorce insignias con nombre propio en tres familias (rachas, constancia y momentos) que se desbloquean solas con tu historial y no se pierden nunca. Sección de Logros en Estadísticas y lista completa con la fecha de desbloqueo o cómo se gana cada una.
- Celebración de día perfecto: Habi lo celebra dentro de la app (una sola vez, también si lo cierras desde el repaso) y, si lo completas desde el widget o una notificación con la app cerrada, te lo cuenta con un aviso en su voz, en un canal propio. Un interruptor en Ajustes controla solo ese aviso; dentro de la app siempre se celebra.
- Cada logro nuevo se celebra con su hoja y la voz de la personalidad activa.

### Changed

- El repaso deja de avisar cuando el día ya está sellado, aunque queden hábitos sin hacer; un hábito de dejar algo con recaída registrada tampoco mantiene el aviso.
- El sonido de celebración de Habi ya no suena al completar el anillo de Hoy: suena con el día perfecto y con cada logro.
- La copia de seguridad pasa a la versión 3 (guarda qué celebraciones has visto); las copias anteriores siguen restaurando.

## [0.6.0] - 2026-08-19

### Added

- Habi cobra vida: pantalla propia con pestaña en la barra inferior, escenario dinámico que refleja su estado de ánimo, selector de personalidad (Sargento, Animadora o Neutra) que cambia cómo habla, y tienda de accesorios con prueba en vivo antes de comprar. Colores, patrones y accesorios se ganan con puntos; cuatro artículos exclusivos se desbloquean por alcanzar rachas.
- Habi te saluda con un mensaje cuando abres la app desde la pantalla Hoy y comenta tu progreso en Estadísticas con su voz según su personalidad.
- Habi aparece en el widget con su estado de ánimo actual.
- Sonidos suaves de Habi para cada momento: saludo al tocarlo en su pantalla, celebración por éxito, sonido de compra al acceder a accesorios, y sonido de recaída. Un interruptor en Ajustes silencia todos los sonidos de Habi.

### Changed

- Los congeladores de racha ahora se compran en la tienda de Habi por 100 puntos; es el precio final tras la sesión de economía.
- El detalle del hábito conserva la vista de tu inventario (colores, patrones, accesorios) y explica cómo se usan.
- Todos los precios en puntos y las cantidades en recompensas quedan fijados por Habi.

### Fixed

- El porcentaje semanal en Estadísticas ahora coincide con las filas cuando un hábito se archiva en mitad de la semana.
- La sección de hábitos pausados respeta el orden en que los pausaste manualmente.
- La navegación de la barra inferior ya no acumula pantallas en el historial de atrás.
- Los textos que muestran cantidades usan el plural correcto en todos los casos.
- Mejoras de accesibilidad: las flechas para cambiar mes son más fáciles de pulsar.

## [0.5.0] - 2026-08-18

### Added

- Pantalla de detalle de cada hábito: la racha actual en grande con su récord, un calendario mensual de puntos con el día de hoy destacado, y el porcentaje de cumplimiento a 7 días, 30 días y un año.
- Registro retroactivo desde el calendario: toca cualquier día pasado para corregirlo — marcar hecho, poner el valor exacto, apuntar una recaída o dejar el día limpio y sellado.
- Congeladores de racha: se compran con puntos y se aplican a mano sobre un día fallado para que no rompa la racha (precio provisional hasta la sesión de economía).
- Pantalla de Estadísticas: Habi comenta cómo vas, días perfectos, tu semana hábito a hábito con comparación contra la anterior, y el muro de rachas activas.
- Pantallas de Récords (mejores rachas de siempre, archivados incluidos) y Tus números (totales de registros, puntos, congeladores y hábitos).
- Pausar y archivar hábitos: la pausa no rompe la racha y se ve en las estadísticas; archivar conserva todo el historial y se puede deshacer desde Ajustes.

### Changed

- Tercera ronda de QA por dogfooding: el detalle y las estadísticas se rehacen fieles al diseño — el número de racha enorme con su llama en su propia tarjeta, el calendario siempre completo con puntos gorditos y los días de la semana, el selector 7/30/año a ancho completo, Pausar y Archivar visibles, los puntos de "Tu semana" con sus cuentas N/7 y letras de los días, el muro de rachas con el nombre de cada hábito, los días perfectos del año con su check, y la barra inferior con el nombre de cada pestaña.
- Un icono de información junto a los congeladores explica cómo funcionan; Récords y Tus números ganan contexto (racha actual, secciones con iconos); y si no tienes ningún aviso configurado, Ajustes te lo dice.
- Tocar una tarjeta en Hoy ahora abre su detalle; editar vive en el lápiz del detalle.
- "He recaído" se muda de la tarjeta de Hoy al detalle del hábito.
- La barra inferior gana la pestaña de Estadísticas.
- Los hábitos en pausa aparecen agrupados al final de Hoy para poder reanudarlos con un toque.

### Fixed

- Los avisos ya no mienten: registrar dentro de la app actualiza o retira la notificación pendiente, y las actualizaciones no vuelven a sonar.
- Al recuperar el permiso de alarmas exactas, los avisos vuelven a ser puntuales al momento, sin esperar al siguiente disparo.
- Crear un recordatorio por hábito ahora pide el permiso de notificaciones si falta.
- En los hábitos de dejar algo, la fila de recordatorio explica por qué no hay avisos (Bito no te recuerda lo que intentas evitar).
- El progreso semanal ya no se infla con registros hechos en días pausados.
- Archivar un hábito pausado cierra la pausa para que al reactivarlo vuelva a contar de verdad.

## [0.4.0] - 2026-08-16

### Added

- Widget "Hoy" configurable y multi-instancia, con registro directo desde el escritorio.
- Recordatorios globales y por hábito.
- Acciones rápidas para registrar desde la propia notificación sin abrir la app.
- Notificación de repaso con protección anti-spam, solo cuando queda algo pendiente.
- Hora de corte del día configurable.
- Mejoras de QA por dogfooding: Eliminar en rojo, teclado en mayúscula inicial, stepper ±10 con valor directo, recordatorio por hábito en el formulario y blindaje del doble toque en Guardar.
- Reordenar hábitos en Hoy manteniendo pulsada la tarjeta y arrastrando.
- Chip visible de valor exacto en los hábitos de tiempo (el toque largo en la barra queda como atajo).

### Changed

- Segunda ronda de QA por dogfooding: barra de navegación persistente también en Ajustes, avisos de "Deshacer" con el diseño propio de la app, el flujo de recaída en rojo destructivo, la fila del stepper ±10 rediseñada con chips uniformes con borde, el selector de hora ya no sale cortado ni desalineado, y los textos de Ajustes explicados para cualquiera ("Fin de tu día" con su porqué, avisos y repaso con descripción).

## [0.3.0] - 2026-08-15

### Added

- Pantalla Hoy con registro completo: un toque, +paso, valor exacto, chips de duración y recaída, todos con deshacer.
- Sellado en lote de los días sin registrar tras estar fuera de la app.
- Alta, edición y borrado de hábitos con los cinco tipos predefinidos.
- Copia de seguridad manual completa: exportación e importación con vista previa antes de restaurar.
- Motor de puntos que reconcilia automáticamente las recompensas ganadas.
- Cimientos del proyecto (M0): scaffold Android (Kotlin + Compose, minSdk 26), tema único "Crema" con tipografía Outfit, icono adaptativo "Habi asomando" (con variante monocroma), CI y estructura de repositorio.
