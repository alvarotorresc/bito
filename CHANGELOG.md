# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/). Versionado [SemVer](https://semver.org/lang/es/).

## [0.5.0] - 2026-08-18

### Added

- Pantalla de detalle de cada hábito: la racha actual en grande con su récord, un calendario mensual de puntos con el día de hoy destacado, y el porcentaje de cumplimiento a 7 días, 30 días y un año.
- Registro retroactivo desde el calendario: toca cualquier día pasado para corregirlo — marcar hecho, poner el valor exacto, apuntar una recaída o dejar el día limpio y sellado.
- Congeladores de racha: se compran con puntos y se aplican a mano sobre un día fallado para que no rompa la racha (precio provisional hasta la sesión de economía).
- Pantalla de Estadísticas: Habi comenta cómo vas, días perfectos, tu semana hábito a hábito con comparación contra la anterior, y el muro de rachas activas.
- Pantallas de Récords (mejores rachas de siempre, archivados incluidos) y Tus números (totales de registros, puntos, congeladores y hábitos).
- Pausar y archivar hábitos: la pausa no rompe la racha y se ve en las estadísticas; archivar conserva todo el historial y se puede deshacer desde Ajustes.

### Changed

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
