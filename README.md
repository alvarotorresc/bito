# Bito 🫘

> Hábitos que se quedan. Un tracker de hábitos con mascota, 100% offline y libre.

**⚠️ En construcción** — Bito está en desarrollo activo y aún no tiene release instalable.

## Qué es

Bito es una app Android de seguimiento de hábitos construida sobre una idea: **registrar un hábito debe costar casi cero**. Widget en tu pantalla de inicio, acciones rápidas en las notificaciones, y un repaso de fin de día que cierra el círculo.

Y vive Habi: una criatura con tres personalidades (del sargento que te grita al ánimo incondicional), cuyo humor refleja tu constancia, y a la que vistes con los puntos que te gana tu disciplina.

## Principios

- **100% offline** — sin cuentas, sin servidores, sin nube. Este manifest no declara el permiso `INTERNET`: compruébalo.
- **Cero telemetría** — ni analytics, ni trackers, ni crash reporting externo.
- **Tus datos son tuyos** — backups completos en un JSON legible, con cifrado opcional, en la carpeta que tú elijas.
- **Software libre** — GPL-3.0-or-later, para siempre.

## Tech

Kotlin · Jetpack Compose · Glance · Room · minSdk 26. Sin Google Play Services (funciona en GrapheneOS).

## Desarrollo local

Requisitos: JDK 21 y Android SDK (API 35).

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew assembleDebug
```

## Licencia

[GPL-3.0-or-later](./LICENSE). La tipografía [Outfit](https://github.com/Outfitio/Outfit-Fonts) se distribuye bajo licencia [OFL](./THIRD_PARTY_LICENSES/outfit-OFL.txt).

El nombre "Bito" y la identidad de Habi son la marca de este proyecto: los forks son bienvenidos (es la gracia del software libre), pero deben usar otro nombre y otra mascota para no confundir a los usuarios.
