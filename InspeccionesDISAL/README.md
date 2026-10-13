# Inspecciones DISAL — piloto Android

Aplicación Android independiente para inspecciones de Seguridad Laboral en Dirección de Salud, Ilustre Municipalidad de Santiago. Piloto sin autorización institucional implícita.

Arquitectura: Kotlin + Jetpack Compose; Room/SQLite offline-first; cámara mediante TakePicture y FileProvider; firmas manuscritas Canvas; informes PDF A4 Android PdfDocument y CSV; compartir mediante Android Sharesheet. No necesita conexión de Internet.

Datos: Inspeccion, Participante, Hallazgo y BitacoraEstado. Los cambios y cierres se registran en auditoría; el inspector valida normativa manualmente. Las firmas son imágenes manuscritas, no firma electrónica avanzada.

Métrica mensual: cohorte de inspecciones fechadas dentro del mes consultado. Cumplimiento = cerrados dentro de plazo / (cerrados dentro de plazo + vencidos) * 100. Pendientes con plazo futuro y hallazgos sin fecha son categorías separadas. Los cierres posteriores al plazo cuentan como vencidos. Las reaperturas se auditan y eliminan el cierre vigente.

Privacidad: nombres, RUT, fotos y firmas permanecen en almacenamiento privado; sin copia de seguridad automática, autenticación, cifrado de aplicación ni servidor remoto en esta versión. No cargar información real al repositorio GitHub (es público). Revisar las políticas de protección de datos y distribución institucional antes de usar en producción. Los PDF son borradores internos revisables.

Instalación: descargar el APK debug desde Actions > Generar Inspecciones DISAL APK > Artifacts. Compilar con JDK 17 y Gradle 8.11.1; minSdk 26, compileSdk 35.
