# ComparaRM 0.3 — piloto Android de comparación de supermercados chilenos

**Descarga de la última compilación:** [GitHub Actions](https://github.com/estebanvivancoo-gif/ComparaRM/actions/workflows/android.yml).

## Cobertura actual

| Cadena | Integración | Observación |
|---|---|---|
| Jumbo | Consulta automática experimental | Precios de catálogo online, no necesariamente por sucursal |
| Santa Isabel | Consulta automática experimental | Precios de catálogo online, no necesariamente por sucursal |
| Unimarc | Consulta automática experimental | Fuente pública BFF verificada en pruebas del 13-10-2026 |
| Tottus | Intento de consulta automática + enlace | Puede bloquear HTTP (403), sin garantía de resultados |
| Líder | Intento de consulta automática + enlace | Puede responder sin productos legibles o bloquear accesos |
| aCuenta | Enlace al sitio | Sin fuente de precios automática verificada |
| Mayorista 10 | Enlace a ofertas | Sin búsqueda de precios por producto verificada |

**No equiparar resultados**: Buscar "leche" puede devolver marcas y tamaños distintos. Una lista de precios devueltos NO acredita que sean el mismo producto. No comparar como iguales hasta verificar código GTIN/EAN y presentación. Las ofertas en línea, los beneficios de clientes y los precios presenciales pueden diferir. Los datos ficticios del piloto siguen separados de las consultas online y observaciones manuales.

## Compilar

El flujo `.github/workflows/android.yml` descomprime el proyecto original `ComparaRM_v0.1_Android.zip`, aplica las mejoras de `patches/apply_v02.py` y `patches/OnlinePrices.java`, ejecuta Gradle para Android y publica un APK de depuración en los artefactos. Las actualizaciones de APK debug en distintos runners pueden usar claves de firma diferentes; si no se instala sobre una versión anterior, se debe respaldar cualquier dato y desinstalar la versión previa. Desinstalar borra los datos locales.

Esta es una prueba técnica y no una aplicación afiliada a los supermercados. Consultar conforme a los términos aplicables de cada sitio antes de distribuir comercialmente o escalar el tráfico.
