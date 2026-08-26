# Reglas de ProGuard/R8 específicas de Bito.
#
# El grueso ya lo aportan las consumer rules de Room, WorkManager, Glance,
# kotlinx.serialization, DataStore y AppCompat: el build de release no genera
# missing_rules.txt, y mapping/seeds confirman que sobreviven los componentes
# del manifest, los $$serializer, el ctor de LogHabitAction y los nombres de
# los enums que cruzan DataStore y el JSON de backup.

# R8 conserva las líneas dentro de mapping.txt, pero sin SourceFile un stack
# trace llega como "Unknown Source" y retrace ya no puede recuperarlo: es la
# diferencia entre poder diagnosticar el crash que reporte alguien y no poder.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Glance instancia los ActionCallback por reflexión y glance-appwidget solo
# envía un -keep de clase; en full mode eso no garantiza por contrato el ctor
# por defecto, y nada lo llama desde el código. Hoy sobrevive igualmente: esto
# es un seguro barato ante un bump de AGP/R8 que cambie ese comportamiento.
-keepclassmembers class * extends androidx.glance.appwidget.action.ActionCallback {
    <init>();
}

# Estos enums cruzan una frontera de persistencia por NOMBRE: DataStore vía
# ::valueOf y el fichero de backup vía kotlinx.serialization. Si R8 llegara a
# reescribir esas cadenas el fallo no sería un crash, sino corrupción silenciosa
# entre versiones: backups viejos ilegibles y ajustes que revierten solos.
-keepclassmembers enum com.alvarotc.bito.domain.model.**,
                       com.alvarotc.bito.data.settings.**,
                       com.alvarotc.bito.data.db.TimeBucket {
    <fields>;
}
