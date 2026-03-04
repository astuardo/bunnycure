# PowerShell script para diagnostico avanzado
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "DIAGNOSTICO AVANZADO - BUNNYCURE" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check BookingRequest.java
Write-Host "[1] Verificando BookingRequest.java..." -ForegroundColor Yellow
$bookingRequestPath = "src\main\java\cl\bunnycure\domain\model\BookingRequest.java"
if (Test-Path $bookingRequestPath) {
    $content = Get-Content $bookingRequestPath -Raw
    Write-Host "    ✓ Archivo existe ($($content.Length) bytes)" -ForegroundColor Green
    
    # Check package declaration
    if ($content -match "package\s+cl\.bunnycure\.domain\.model;") {
        Write-Host "    ✓ Package declaration correcto" -ForegroundColor Green
    } else {
        Write-Host "    ✗ Package declaration incorrecto" -ForegroundColor Red
    }
    
    # Check class declaration
    if ($content -match "public\s+class\s+BookingRequest") {
        Write-Host "    ✓ Class declaration correcto" -ForegroundColor Green
    } else {
        Write-Host "    ✗ Class declaration no encontrado" -ForegroundColor Red
    }
    
    # Check for syntax errors (basic)
    $lines = Get-Content $bookingRequestPath
    $braceCount = 0
    foreach ($line in $lines) {
        $braceCount += ($line.ToCharArray() | Where-Object { $_ -eq '{' }).Count
        $braceCount -= ($line.ToCharArray() | Where-Object { $_ -eq '}' }).Count
    }
    if ($braceCount -eq 0) {
        Write-Host "    ✓ Braces balanceadas" -ForegroundColor Green
    } else {
        Write-Host "    ✗ Braces desbalanceadas (diferencia: $braceCount)" -ForegroundColor Red
    }
} else {
    Write-Host "    ✗ Archivo NO existe" -ForegroundColor Red
}

# Check target directory
Write-Host ""
Write-Host "[2] Verificando directorio target..." -ForegroundColor Yellow
if (Test-Path "target") {
    $targetSize = (Get-ChildItem -Path "target" -Recurse -File | Measure-Object -Property Length -Sum).Sum / 1MB
    Write-Host "    ! Target existe ($([math]::Round($targetSize, 2)) MB)" -ForegroundColor Yellow
    Write-Host "    Recomendacion: Eliminar para limpieza completa" -ForegroundColor Yellow
} else {
    Write-Host "    ✓ Target no existe (listo para compilacion limpia)" -ForegroundColor Green
}

# Check for duplicate class files
Write-Host ""
Write-Host "[3] Buscando archivos duplicados..." -ForegroundColor Yellow
$javaFiles = Get-ChildItem -Path "src" -Filter "*.java" -Recurse
$duplicates = $javaFiles | Group-Object Name | Where-Object { $_.Count -gt 1 }
if ($duplicates) {
    Write-Host "    ✗ Se encontraron archivos duplicados:" -ForegroundColor Red
    foreach ($dup in $duplicates) {
        Write-Host "      - $($dup.Name) ($($dup.Count) copias)" -ForegroundColor Red
        foreach ($file in $dup.Group) {
            Write-Host "        $($file.FullName)" -ForegroundColor Gray
        }
    }
} else {
    Write-Host "    ✓ No hay archivos duplicados" -ForegroundColor Green
}

# Check Lombok dependency
Write-Host ""
Write-Host "[4] Verificando Lombok en pom.xml..." -ForegroundColor Yellow
$pomContent = Get-Content "pom.xml" -Raw
if ($pomContent -match "projectlombok.*lombok") {
    Write-Host "    ✓ Lombok encontrado en pom.xml" -ForegroundColor Green
} else {
    Write-Host "    ✗ Lombok NO encontrado en pom.xml" -ForegroundColor Red
}

# Check all domain model files
Write-Host ""
Write-Host "[5] Verificando todos los archivos del modelo..." -ForegroundColor Yellow
$modelPath = "src\main\java\cl\bunnycure\domain\model"
if (Test-Path $modelPath) {
    $modelFiles = Get-ChildItem -Path $modelPath -Filter "*.java"
    Write-Host "    Archivos encontrados:" -ForegroundColor White
    foreach ($file in $modelFiles) {
        $fileContent = Get-Content $file.FullName -Raw
        $hasPackage = $fileContent -match "package\s+cl\.bunnycure\.domain\.model;"
        $hasClass = $fileContent -match "public\s+(class|enum|interface)"
        
        if ($hasPackage -and $hasClass) {
            Write-Host "      ✓ $($file.Name)" -ForegroundColor Green
        } else {
            Write-Host "      ✗ $($file.Name) (posible error de sintaxis)" -ForegroundColor Red
        }
    }
}

# Summary and recommendations
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "RECOMENDACIONES" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "1. Ejecutar: diagnosticar-y-reparar.cmd" -ForegroundColor Yellow
Write-Host "2. Si persiste: File > Invalidate Caches > Invalidate and Restart" -ForegroundColor Yellow
Write-Host "3. Después: Click derecho en pom.xml > Maven > Reload Project" -ForegroundColor Yellow
Write-Host ""

Read-Host "Presiona Enter para cerrar"
