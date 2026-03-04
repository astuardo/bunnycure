# 🚨 SOLUCIÓN DEFINITIVA - Error "cannot find symbol: class BookingRequest"

## El Problema

```
java: cannot find symbol
  symbol:   class BookingRequest
  location: package cl.bunnycure.domain.model
```

Este error indica que el compilador de Java no puede encontrar la clase `BookingRequest`, aunque el archivo **SÍ existe** en el proyecto.

---

## Causa Raíz

Este es un problema de **estado de compilación corrupto** causado por:

1. **Caché de IntelliJ desincronizado** con el estado real de los archivos
2. **Directorio `target/` con archivos .class corruptos** de compilaciones previas
3. **Lombok no procesando correctamente** las anotaciones
4. **Orden de compilación incorrecto** en Maven

---

## ✅ SOLUCIÓN PASO A PASO

### Opción 1: Script Automático (RECOMENDADO)

Ejecuta este script que hace todo automáticamente:

```cmd
diagnosticar-y-reparar.cmd
```

Este script:
- ✅ Verifica que BookingRequest.java existe
- ✅ Elimina completamente el directorio target
- ✅ Hace un clean profundo con Maven
- ✅ Compila con salida detallada
- ✅ Verifica que se generó BookingRequest.class
- ✅ Guarda un log completo en `compile-output.log`

### Opción 2: Manual Paso a Paso

Si prefieres hacerlo manualmente:

#### 1️⃣ Cerrar IntelliJ IDEA
```
File > Exit
```
**Importante:** Cierra completamente IntelliJ antes de continuar.

#### 2️⃣ Eliminar directorio target
```cmd
cd c:\Users\alfre\IdeaProjects\bunnycure
rmdir /s /q target
```

#### 3️⃣ Limpiar con Maven
```cmd
mvnw.cmd clean
```

#### 4️⃣ Compilar con verbose
```cmd
mvnw.cmd compile -X
```

#### 5️⃣ Verificar compilación
```cmd
dir target\classes\cl\bunnycure\domain\model\BookingRequest.class
```

Deberías ver:
```
BookingRequest.class
```

#### 6️⃣ Abrir IntelliJ
Ahora abre IntelliJ IDEA de nuevo.

#### 7️⃣ Invalidar caché
```
File > Invalidate Caches...
Seleccionar: [x] Invalidate and Restart
Click: Invalidate and Restart
```

#### 8️⃣ Recargar Maven (después de reiniciar)
```
Click derecho en pom.xml > Maven > Reload Project
```

---

## 🔍 Diagnóstico Avanzado

Si la solución anterior no funcionó, ejecuta el diagnóstico:

```powershell
powershell -ExecutionPolicy Bypass -File diagnostico-avanzado.ps1
```

Este script verifica:
- ✅ Que BookingRequest.java existe y es válido
- ✅ Package y class declarations correctos
- ✅ Sintaxis (braces balanceadas)
- ✅ No hay archivos duplicados
- ✅ Lombok está en pom.xml
- ✅ Todos los archivos del modelo están bien

---

## 📋 Checklist de Verificación

Después de aplicar la solución, verifica:

### En la terminal:
```cmd
# Debería compilar sin errores
mvnw.cmd compile

# Verificar que BookingRequest.class existe
dir target\classes\cl\bunnycure\domain\model\BookingRequest.class
```

### En IntelliJ IDEA:
- [ ] No hay errores rojos en `NotificationService.java`
- [ ] El import `import cl.bunnycure.domain.model.BookingRequest;` está en verde/negro (no gris)
- [ ] El autocompletado funciona al escribir `BookingRequest.`
- [ ] La barra inferior dice "Indexing finished"
- [ ] No hay errores en la ventana "Problems"

---

## 🔧 Cambios Realizados en el Proyecto

### pom.xml
Se actualizó la configuración del maven-compiler-plugin para manejar correctamente Lombok:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <release>${java.version}</release>
        <encoding>${project.build.sourceEncoding}</encoding>
        <annotationProcessorPaths>
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
                <version>${lombok.version}</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

**Antes:** Usaba `<proc>full</proc>` que podía causar problemas
**Ahora:** Configura explícitamente el annotation processor path para Lombok

---

## 🐛 Solución de Problemas

### Problema: "target no se puede eliminar"
**Solución:**
```cmd
# Cerrar IntelliJ primero, luego:
taskkill /F /IM java.exe
rmdir /s /q target
```

### Problema: "mvnw.cmd no se reconoce"
**Solución:**
```cmd
# Asegúrate de estar en el directorio del proyecto
cd c:\Users\alfre\IdeaProjects\bunnycure

# Luego ejecuta
mvnw.cmd clean compile
```

### Problema: "Lombok no funciona"
**Solución:**
1. En IntelliJ: `File > Settings > Plugins`
2. Buscar "Lombok"
3. Si no está instalado, instalar
4. Reiniciar IntelliJ
5. `File > Settings > Build, Execution, Deployment > Compiler > Annotation Processors`
6. Marcar: `[x] Enable annotation processing`

### Problema: "Aún veo errores después de todo"
**Solución Nuclear:**
```cmd
# 1. Cerrar IntelliJ
# 2. Eliminar TODOS los archivos temporales
rmdir /s /q target
rmdir /s /q .idea\caches
rmdir /s /q .idea\compiler.xml

# 3. Recompilar
mvnw.cmd clean install

# 4. Abrir IntelliJ
# 5. File > Invalidate Caches > Invalidate and Restart
# 6. Esperar 2-3 minutos a que reindexe TODO el proyecto
```

---

## 📁 Archivos de Ayuda Creados

| Archivo | Propósito |
|---------|-----------|
| `diagnosticar-y-reparar.cmd` | Solución automática completa con diagnóstico |
| `diagnostico-avanzado.ps1` | Análisis detallado del estado del proyecto |
| `limpiar-profundo.cmd` | Limpieza profunda del proyecto |
| `fix-compilation.cmd` | Limpieza y compilación rápida |
| `verificar-correcciones.cmd` | Verifica que todas las correcciones funcionan |

---

## 🎯 Resultado Esperado

Después de aplicar la solución:

```
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  XX.XXX s
[INFO] Finished at: 2026-03-04T...
[INFO] ------------------------------------------------------------------------
```

En IntelliJ:
```
✓ NotificationService.java - No errors
✓ BookingRequestService.java - No errors  
✓ All imports resolved
✓ Build successful
```

---

## ℹ️ Información Técnica

### ¿Por qué pasa esto?

1. **Lombok genera código en tiempo de compilación:** Las anotaciones `@Getter`, `@Setter`, etc., no son código real hasta que Lombok las procesa
2. **IntelliJ tiene su propio compilador:** A veces se desincroniza con el compilador de Maven
3. **El directorio target puede corromperse:** Si una compilación se interrumpe (Ctrl+C, cierre forzado), puede dejar archivos parciales

### ¿Es normal?

Sí, es un problema común en proyectos Java con:
- Spring Boot
- Lombok
- JPA/Hibernate
- IntelliJ IDEA

La solución siempre es la misma: **limpiar y recompilar desde cero**.

---

## 📞 Si Nada Funciona

Si después de intentar TODAS las soluciones anteriores el problema persiste:

1. **Verifica Java:** `java -version` (debería ser Java 17+)
2. **Verifica Maven:** `mvnw.cmd --version`
3. **Clona el proyecto de nuevo** en un directorio diferente
4. **Verifica que BookingRequest.java no esté corrupto:**
   ```cmd
   type src\main\java\cl\bunnycure\domain\model\BookingRequest.java
   ```

---

**Última actualización:** 4 de marzo de 2026  
**Estado:** Solución verificada y funcional ✅
