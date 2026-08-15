# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/). Versionado [SemVer](https://semver.org/lang/es/).

## [0.4.0] - 2026-08-15

### Added

- Configurable, multi-instance Today widget with direct tap logging from the home screen.
- Global and per-habit reminders with quick actions to log straight from the notification.
- Day-review notification that only fires when something is pending, with anti-spam guarding.
- Configurable day cutoff, so the logical day and the widget/reminder rotation follow it.
- Dogfooding QA fixes: red delete affordance, sentence-case keyboard input, a ±10 minute stepper with direct value entry, a per-habit reminder row in the habit form, and a double-tap-proof save guard.

## [0.3.0] - 2026-08-15

### Added

- Pantalla Hoy con registro completo: un toque, +paso, valor exacto, chips de duración y recaída, todos con deshacer.
- Sellado en lote de los días sin registrar tras estar fuera de la app.
- Alta, edición y borrado de hábitos con los cinco tipos predefinidos.
- Copia de seguridad manual completa: exportación e importación con vista previa antes de restaurar.
- Motor de puntos que reconcilia automáticamente las recompensas ganadas.
- Cimientos del proyecto (M0): scaffold Android (Kotlin + Compose, minSdk 26), tema único "Crema" con tipografía Outfit, icono adaptativo "Habi asomando" (con variante monocroma), CI y estructura de repositorio.
