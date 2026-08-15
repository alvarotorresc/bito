# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/). Versionado [SemVer](https://semver.org/lang/es/).

## [0.4.0] - 2026-08-16

### Added

- Widget "Hoy" configurable y multi-instancia, con registro directo desde el escritorio.
- Recordatorios globales y por hábito con acciones rápidas para registrar desde la propia notificación.
- Notificación de repaso con protección anti-spam, solo cuando queda algo pendiente.
- Hora de corte del día configurable.
- Mejoras de QA por dogfooding: Eliminar en rojo, teclado en mayúscula inicial, stepper ±10 con valor directo, recordatorio por hábito en el formulario y blindaje del doble toque en Guardar.

## [0.3.0] - 2026-08-15

### Added

- Pantalla Hoy con registro completo: un toque, +paso, valor exacto, chips de duración y recaída, todos con deshacer.
- Sellado en lote de los días sin registrar tras estar fuera de la app.
- Alta, edición y borrado de hábitos con los cinco tipos predefinidos.
- Copia de seguridad manual completa: exportación e importación con vista previa antes de restaurar.
- Motor de puntos que reconcilia automáticamente las recompensas ganadas.
- Cimientos del proyecto (M0): scaffold Android (Kotlin + Compose, minSdk 26), tema único "Crema" con tipografía Outfit, icono adaptativo "Habi asomando" (con variante monocroma), CI y estructura de repositorio.
