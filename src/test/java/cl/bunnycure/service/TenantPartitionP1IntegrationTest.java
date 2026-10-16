package cl.bunnycure.service;

import cl.bunnycure.config.TenantContext;
import cl.bunnycure.config.TestMailConfiguration;
import cl.bunnycure.domain.enums.OutboxStatus;
import cl.bunnycure.domain.model.IncomingWhatsAppMessage;
import cl.bunnycure.domain.model.LoyaltyReward;
import cl.bunnycure.domain.model.WhatsAppOutboxMessage;
import cl.bunnycure.domain.repository.AppSettingsRepository;
import cl.bunnycure.domain.repository.IncomingWhatsAppMessageRepository;
import cl.bunnycure.domain.repository.LoyaltyRewardRepository;
import cl.bunnycure.domain.repository.WhatsAppOutboxMessageRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aislamiento por salón de las entidades que eran globales antes de V70:
 * configuración, catálogo de recompensas, bandeja de WhatsApp y cola de envíos fallidos.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestMailConfiguration.class)
@DisplayName("Partición por salón (P1): settings, recompensas y WhatsApp")
class TenantPartitionP1IntegrationTest {

    private static final String KEY = "test.p1.salon-name";

    @Autowired private AppSettingsService appSettingsService;
    @Autowired private AppSettingsRepository appSettingsRepository;
    @Autowired private LoyaltyRewardRepository loyaltyRewardRepository;
    @Autowired private IncomingWhatsAppMessageRepository incomingRepository;
    @Autowired private WhatsAppOutboxMessageRepository outboxRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    private final List<Runnable> cleanups = new ArrayList<>();

    @AfterEach
    void tearDown() {
        cleanups.forEach(Runnable::run);
        cleanups.clear();
        appSettingsService.clearCache();
        TenantContext.clear();
    }

    @Test
    @DisplayName("Settings: la misma clave tiene valor propio por salón y actualizar uno no pisa al otro")
    void settings_AreIndependentPerTenant() {
        cleanupSettingInBothTenants();

        inTenant(1L, () -> { appSettingsService.set(KEY, "Bunny Cure"); return null; });
        inTenant(2L, () -> { appSettingsService.set(KEY, "Glamour"); return null; });

        // Lecturas (cacheadas por salón)
        assertThat(inTenant(1L, () -> appSettingsService.get(KEY, "-"))).isEqualTo("Bunny Cure");
        assertThat(inTenant(2L, () -> appSettingsService.get(KEY, "-"))).isEqualTo("Glamour");

        // Actualizar en el salón 2 no debe afectar al salón 1 (UPDATE por id, no por clave)
        inTenant(2L, () -> { appSettingsService.set(KEY, "Glamour Studio"); return null; });
        appSettingsService.clearCache();

        assertThat(inTenant(1L, () -> appSettingsService.get(KEY, "-"))).isEqualTo("Bunny Cure");
        assertThat(inTenant(2L, () -> appSettingsService.get(KEY, "-"))).isEqualTo("Glamour Studio");

        // Un salón sin la clave recibe el valor por defecto, no el de otro salón
        assertThat(inTenant(3L, () -> appSettingsService.get(KEY, "default"))).isEqualTo("default");
    }

    @Test
    @DisplayName("Recompensas de fidelización: cada salón ve solo su catálogo")
    void loyaltyRewards_AreScopedByTenant() {
        Long r1 = inTenant(1L, () -> loyaltyRewardRepository.save(reward("Manicure gratis")).getId());
        Long r2 = inTenant(2L, () -> loyaltyRewardRepository.save(reward("Pedicure gratis")).getId());
        cleanups.add(() -> inTenant(1L, () -> { loyaltyRewardRepository.deleteById(r1); return null; }));
        cleanups.add(() -> inTenant(2L, () -> { loyaltyRewardRepository.deleteById(r2); return null; }));

        List<Long> seenBy1 = inTenant(1L, () -> loyaltyRewardRepository.findAllByOrderByOrderIndexAsc()
                .stream().map(LoyaltyReward::getId).toList());

        assertThat(seenBy1).contains(r1).doesNotContain(r2);
        assertThat(inTenant(1L, () -> loyaltyRewardRepository.findById(r2))).isEmpty();
    }

    @Test
    @DisplayName("Bandeja de WhatsApp: aislada por salón, incluido el 'marcar todo como leído'")
    void incomingMessages_AreScopedByTenant_IncludingBulkUpdate() {
        Long m1 = inTenant(1L, () -> incomingRepository.save(incoming("wamid.p1.salon1")).getId());
        Long m2 = inTenant(2L, () -> incomingRepository.save(incoming("wamid.p1.salon2")).getId());
        cleanups.add(() -> inTenant(1L, () -> { incomingRepository.deleteById(m1); return null; }));
        cleanups.add(() -> inTenant(2L, () -> { incomingRepository.deleteById(m2); return null; }));

        List<Long> inboxOf1 = inTenant(1L, () -> incomingRepository.findAllByOrderByCreatedAtDesc(Pageable.ofSize(50))
                .stream().map(IncomingWhatsAppMessage::getId).toList());
        assertThat(inboxOf1).contains(m1).doesNotContain(m2);

        inTenant(1L, incomingRepository::markAllAsRead);

        assertThat(inTenant(1L, () -> incomingRepository.findById(m1).orElseThrow().isRead())).isTrue();
        assertThat(inTenant(2L, () -> incomingRepository.findById(m2).orElseThrow().isRead()))
                .as("'Marcar todo como leído' del salón 1 no debe tocar la bandeja del salón 2")
                .isFalse();
    }

    @Test
    @DisplayName("Cola de WhatsApp fallidos: aislada por salón, incluido el cambio de estado masivo")
    void outboxMessages_AreScopedByTenant_IncludingBulkUpdate() {
        Long o1 = inTenant(1L, () -> outboxRepository.save(outbox()).getId());
        Long o2 = inTenant(2L, () -> outboxRepository.save(outbox()).getId());
        cleanups.add(() -> inTenant(1L, () -> { outboxRepository.deleteById(o1); return null; }));
        cleanups.add(() -> inTenant(2L, () -> { outboxRepository.deleteById(o2); return null; }));

        List<Long> seenBy1 = inTenant(1L, () -> outboxRepository.findAllByOrderByCreatedAtDesc(Pageable.ofSize(50))
                .stream().map(WhatsAppOutboxMessage::getId).toList());
        assertThat(seenBy1).contains(o1).doesNotContain(o2);

        inTenant(1L, () -> outboxRepository.updateStatusForStatuses(OutboxStatus.DISCARDED, List.of(OutboxStatus.FAILED)));

        assertThat(inTenant(1L, () -> outboxRepository.findById(o1).orElseThrow().getStatus())).isEqualTo(OutboxStatus.DISCARDED);
        assertThat(inTenant(2L, () -> outboxRepository.findById(o2).orElseThrow().getStatus()))
                .as("Descartar en el salón 1 no debe tocar la cola del salón 2")
                .isEqualTo(OutboxStatus.FAILED);
    }

    private void cleanupSettingInBothTenants() {
        Runnable delete = () -> {
            for (Long tenant : List.of(1L, 2L)) {
                inTenant(tenant, () -> {
                    appSettingsRepository.findByKey(KEY).ifPresent(appSettingsRepository::delete);
                    return null;
                });
            }
        };
        delete.run();
        cleanups.add(delete);
    }

    private static LoyaltyReward reward(String name) {
        LoyaltyReward reward = new LoyaltyReward();
        reward.setName(name);
        reward.setOrderIndex(0);
        return reward;
    }

    private static IncomingWhatsAppMessage incoming(String wamid) {
        return IncomingWhatsAppMessage.builder()
                .wamid(wamid)
                .fromPhone("56911112222")
                .content("Hola, ¿tienen hora mañana?")
                .build();
    }

    private static WhatsAppOutboxMessage outbox() {
        return WhatsAppOutboxMessage.builder()
                .recipientPhone("56911112222")
                .status(OutboxStatus.FAILED)
                .build();
    }

    private <T> T inTenant(Long tenantId, Supplier<T> action) {
        Long previous = TenantContext.getExplicitTenantId();
        try {
            TenantContext.setCurrentTenantId(tenantId);
            return transactionTemplate.execute(status -> action.get());
        } finally {
            TenantContext.setCurrentTenantId(previous);
        }
    }
}
