# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/). Versionado [SemVer](https://semver.org/lang/es/).

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
