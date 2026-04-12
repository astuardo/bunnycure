package cl.bunnycure.web.controller;

import cl.bunnycure.service.StatsService;
import cl.bunnycure.web.dto.ApiResponse;
import cl.bunnycure.web.dto.DashboardStatsDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
