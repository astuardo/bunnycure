package cl.bunnycure.web.controller;

import cl.bunnycure.service.StatsService;
import cl.bunnycure.web.dto.ApiResponse;
import cl.bunnycure.web.dto.DashboardStatsDto;
import cl.bunnycure.web.dto.SpecialistStatsDto;
import cl.bunnycure.web.dto.TodayOperationalStatsDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Tag(name = "Stats", description = "API para estadísticas y analíticas")
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsApiController {

    private final StatsService statsService;

    @Operation(summary = "Obtener estadísticas para el dashboard", 
               description = "Retorna ingresos del mes, top servicios y cliente más frecuente.")
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardStatsDto>> getDashboardStats() {
        log.debug("[API] Requesting dashboard stats");
        DashboardStatsDto stats = statsService.getDashboardStats();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @Operation(summary = "Obtener estadísticas operativas del día de hoy",
               description = "Retorna citas del día, progreso en tiempo real, alertas de no-show, ingresos cobrados vs proyectados de hoy.")
    @GetMapping("/today")
    public ResponseEntity<ApiResponse<TodayOperationalStatsDto>> getTodayStats() {
        log.debug("[API] Requesting today operational stats");
        TodayOperationalStatsDto stats = statsService.getTodayOperationalStats();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @Operation(summary = "Obtener rendimiento por especialista",
               description = "Retorna métricas de atenciones, completadas, cancelaciones y recaudación por manicurista/especialista.")
    @GetMapping("/specialists")
    public ResponseEntity<ApiResponse<List<SpecialistStatsDto>>> getSpecialistStats(
            @Parameter(description = "Fecha de inicio (formato: yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @Parameter(description = "Fecha de fin (formato: yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.debug("[API] Requesting specialist stats from {} to {}", startDate, endDate);
        List<SpecialistStatsDto> stats = statsService.getSpecialistStats(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }
}
