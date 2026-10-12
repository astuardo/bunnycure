# 🚀 Pipeline de Staging - BunnyCure Backend

Este documento describe la arquitectura y configuración del pipeline de **Integración y Despliegue Continuo (CI/CD)** para el entorno de **Staging** del backend Spring Boot.

---

## 📋 Flujo de Trabajo (Git Workflow)

1. El desarrollo de nuevas funcionalidades se realiza en ramas `feature/*` o correcciones en `fix/*`.
2. Para probar en el entorno de pre-producción/staging, se realiza un Pull Request hacia la rama **`staging`**.
3. Al hacer merge o push a `staging`, GitHub Actions ejecuta automáticamente el workflow [.github/workflows/backend-staging.yml](file:///.github/workflows/backend-staging.yml).
4. El pipeline ejecuta:
   - **Checkout del código** y configuración de JDK 17 (Eclipse Temurin) con caché de dependencias Maven.
   - **Pruebas unitarias** (`./mvnw clean test -B`).
   - **Empaquetado del JAR ejecutable** (`./mvnw package -DskipTests -B`).
   - **Archivado del artefacto** `target/*.jar` en GitHub Actions (disponible para descarga durante 7 días).
   - **Validación del Dockerfile** mediante construcción de imagen Docker.
   - **Despliegue a Heroku Staging** mediante `git push` autenticado con API Key.

---

## 🔑 Configuración de Secrets en GitHub

Para habilitar el despliegue automático a Heroku Staging, agrega los siguientes **Repository Secrets** en GitHub (`Settings > Secrets and variables > Actions > Secrets`):

| Secret | Descripción | Requerido | Ejemplo |
| :--- | :--- | :---: | :--- |
| `HEROKU_API_KEY` | Clave API de tu cuenta de Heroku ([Heroku Account Settings](https://dashboard.heroku.com/account)) | **Sí** (para auto-deploy) | `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx` |
| `HEROKU_STAGING_APP_NAME` | Nombre de la app de Staging creada en Heroku | Opcional (default: `bunnycure-staging`) | `bunnycure-staging` |
| `HEROKU_EMAIL` | Correo electrónico de tu cuenta de Heroku | Opcional | `tu-email@correo.com` |

> [!NOTE]
> **Modo Tolerante a Fallos:** Si aún no has configurado `HEROKU_API_KEY`, el pipeline no fallará: compilará el código, ejecutará los tests, validará Docker, guardará el archivo `.jar` como artefacto descargable y dejará un reporte claro en el Step Summary con las instrucciones para conectar Heroku.

---

## ⚙️ Configuración del Entorno de Staging en Heroku

En la aplicación de Heroku (`bunnycure-staging`), asegúrate de tener configuradas las siguientes variables de configuración (**Config Vars**):

1. **Base de Datos (Heroku Postgres)**:
   - Provisionar el add-on: `heroku addons:create heroku-postgresql:essential-0 -a bunnycure-staging`
   - La variable `DATABASE_URL` se genera automáticamente.
   - **Flyway Migrations**: Se ejecutan de manera automática al inicializar Spring Boot, aplicando todos los scripts en `src/main/resources/db/migration/`.

2. **Variables de Entorno Clave (`Config Vars`)**:
   ```bash
   SPRING_PROFILES_ACTIVE=heroku
   JWT_SECRET=tu_secreto_super_seguro_para_staging_de_al_menos_256_bits_aqui
   APP_FRONTEND_URL=https://tu-frontend-staging.vercel.app
   ```

---

## 🏃 Disparo Manual (Workflow Dispatch)

Puedes ejecutar el pipeline de Staging de forma manual en cualquier momento desde la interfaz de GitHub:
1. Ir a la pestaña **Actions > Backend Staging - Build & Deploy**.
2. Hacer clic en **Run workflow**.
3. Seleccionar la rama (por ejemplo, `staging` o `main`).
4. Opcionalmente, cambiar el nombre de la app de destino en Heroku.

---

## 📦 Uso del Artefacto Generado

Si necesitas probar el artefacto generado por el pipeline en tu máquina local o en un servidor alternativo:
1. En GitHub Actions, ve a la ejecución del workflow completada.
2. Descarga el archivo comprimido **`backend-staging-jar`**.
3. Ejecútalo localmente:
   ```bash
   java -jar bunnycure-0.0.1-SNAPSHOT.jar
   ```
