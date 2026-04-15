package cl.bunnycure.web.controller;

import cl.bunnycure.domain.model.LoyaltyReward;
import cl.bunnycure.service.LoyaltyRewardService;
import cl.bunnycure.web.dto.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Loyalty Rewards", description = "Gestión de la lista de premios")
@RestController
@RequestMapping("/api/loyalty-rewards")
@RequiredArgsConstructor
public class LoyaltyRewardController {

    private final LoyaltyRewardService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<LoyaltyReward>>> list() {
        return ResponseEntity.ok(ApiResponse.success(service.findAll()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LoyaltyReward>> create(@RequestBody LoyaltyReward reward) {
        return ResponseEntity.ok(ApiResponse.success(service.save(reward)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<LoyaltyReward>> update(@PathVariable Long id, @RequestBody LoyaltyReward reward) {
        reward.setId(id);
        return ResponseEntity.ok(ApiResponse.success(service.save(reward)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reorder")
    public ResponseEntity<ApiResponse<Void>> reorder(@RequestBody List<Long> ids) {
        service.updateOrder(ids);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
