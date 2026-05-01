package cl.bunnycure.service;

import cl.bunnycure.domain.enums.*;
import cl.bunnycure.domain.model.*;
import cl.bunnycure.domain.repository.GiftCardEventRepository;
import cl.bunnycure.domain.repository.GiftCardRepository;
import cl.bunnycure.domain.repository.UserRepository;
import cl.bunnycure.exception.ConflictException;
import cl.bunnycure.exception.ResourceNotFoundException;
import cl.bunnycure.exception.ValidationException;
import cl.bunnycure.web.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GiftCardService {

    private static final ZoneId CHILE_ZONE_ID = ZoneId.of("America/Santiago");
    private static final int PIN_MAX_ATTEMPTS = 5;
    private static final Duration PIN_LOCK_DURATION = Duration.ofMinutes(15);

    private final GiftCardRepository giftCardRepository;
    private final GiftCardEventRepository giftCardEventRepository;
    private final CustomerService customerService;
    private final ServiceCatalogService serviceCatalogService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.frontend.base-url:https://bunnycure-frontend.vercel.app}")
    private String frontendBaseUrl;

    @Transactional
    public GiftCardResponseDto create(
            GiftCardCreateRequestDto request,
            Long actorUserId,
            String actorUsername,
            String ip,
            String userAgent
    ) {
        validateDates(request.getExpiresOn());
        Customer beneficiary = findOrCreateBeneficiary(
                request.getBeneficiaryFullName(),
                request.getBeneficiaryPhone(),
                request.getBeneficiaryEmail()
        );

        List<ItemBuildData> itemBuildData = buildItemData(request.getItems());
        int totalAmount = itemBuildData.stream().mapToInt(data -> data.unitPrice * data.quantity).sum();

        String plainPin = generatePin();
        GiftCard giftCard = GiftCard.builder()
                .code("PENDING-" + UUID.randomUUID().toString().replace("-", ""))
                .pinHash(passwordEncoder.encode(plainPin))
                .status(GiftCardStatus.ACTIVE)
                .beneficiaryCustomer(beneficiary)
                .beneficiaryNameSnapshot(beneficiary.getFullName())
                .beneficiaryPhoneSnapshot(beneficiary.getPhone())
                .beneficiaryEmailSnapshot(normalizeNullable(request.getBeneficiaryEmail(), beneficiary.getEmail()))
                .buyerName(normalizeNullable(request.getBuyerName(), null))
                .buyerPhone(normalizeNullable(request.getBuyerPhone(), null))
                .buyerEmail(normalizeNullable(request.getBuyerEmail(), null))
                .issuedAt(LocalDateTime.now(CHILE_ZONE_ID))
                .expiresOn(request.getExpiresOn())
                .totalAmount(totalAmount)
                .paidAmount(request.getPaidAmount())
                .paymentMethod(request.getPaymentMethod())
                .publicUrl("PENDING")
                .createdByUserId(actorUserId)
                .pinFailedAttempts(0)
                .build();

        giftCard = giftCardRepository.save(giftCard);
        giftCard.setCode(generateCode(giftCard.getId()));
        giftCard.setPublicUrl(buildPublicUrl(giftCard.getCode()));

        final GiftCard persistedGiftCard = giftCard;
        List<GiftCardItem> items = itemBuildData.stream()
                .map(data -> GiftCardItem.builder()
                        .giftCard(persistedGiftCard)
                        .service(data.service)
                        .serviceNameSnapshot(data.service.getName())
                        .unitPriceSnapshot(data.unitPrice)
                        .quantity(data.quantity)
                        .redeemedQuantity(0)
                        .build())
                .toList();

        giftCard.getItems().clear();
        giftCard.getItems().addAll(items);
        giftCard.setStatus(resolveStatus(giftCard));
        giftCard = giftCardRepository.save(giftCard);

        saveEvent(giftCard, null, GiftCardEventType.CREATED, null, "GiftCard creada", GiftCardAuditActor.ADMIN,
                actorUserId, actorUsername, ip, userAgent, null);

        return toDto(giftCard, giftCardEventRepository.findByGiftCardIdOrderByCreatedAtDesc(giftCard.getId()), plainPin);
    }

    @Transactional
    public GiftCardResponseDto update(
            Long id,
            GiftCardCreateRequestDto request,
            Long actorUserId,
            String actorUsername,
            String ip,
            String userAgent
    ) {
        validateDates(request.getExpiresOn());
        GiftCard giftCard = findByIdWithDetails(id);
        refreshExpiryStatus(giftCard);
        ensureEditable(giftCard);

        Customer beneficiary = findOrCreateBeneficiary(
                request.getBeneficiaryFullName(),
                request.getBeneficiaryPhone(),
                request.getBeneficiaryEmail()
        );

        List<ItemBuildData> itemBuildData = buildItemData(request.getItems());
        int totalAmount = itemBuildData.stream().mapToInt(data -> data.unitPrice * data.quantity).sum();

        giftCard.setBeneficiaryCustomer(beneficiary);
        giftCard.setBeneficiaryNameSnapshot(beneficiary.getFullName());
        giftCard.setBeneficiaryPhoneSnapshot(beneficiary.getPhone());
        giftCard.setBeneficiaryEmailSnapshot(normalizeNullable(request.getBeneficiaryEmail(), beneficiary.getEmail()));
        giftCard.setBuyerName(normalizeNullable(request.getBuyerName(), null));
        giftCard.setBuyerPhone(normalizeNullable(request.getBuyerPhone(), null));
        giftCard.setBuyerEmail(normalizeNullable(request.getBuyerEmail(), null));
        giftCard.setExpiresOn(request.getExpiresOn());
        giftCard.setPaidAmount(request.getPaidAmount());
        giftCard.setPaymentMethod(request.getPaymentMethod());
        giftCard.setTotalAmount(totalAmount);

        giftCard.getItems().clear();
        for (ItemBuildData data : itemBuildData) {
            giftCard.getItems().add(GiftCardItem.builder()
                    .giftCard(giftCard)
                    .service(data.service)
                    .serviceNameSnapshot(data.service.getName())
                    .unitPriceSnapshot(data.unitPrice)
                    .quantity(data.quantity)
                    .redeemedQuantity(0)
                    .build());
        }

        giftCard.setStatus(resolveStatus(giftCard));
        giftCard = giftCardRepository.save(giftCard);
        saveEvent(giftCard, null, GiftCardEventType.CREATED, null, "GiftCard actualizada", GiftCardAuditActor.ADMIN,
                actorUserId, actorUsername, ip, userAgent, null);
        return toDto(giftCard, giftCardEventRepository.findByGiftCardIdOrderByCreatedAtDesc(giftCard.getId()), null);
    }

    public List<GiftCardResponseDto> list(String search, GiftCardStatus status, LocalDate expiringBefore) {
        List<GiftCard> giftCards = giftCardRepository.search(search, status, expiringBefore);
        return giftCards.stream()
                .map(gc -> {
                    refreshExpiryStatus(gc);
                    return toDto(gc, null, null);
                })
                .toList();
    }

    public GiftCardResponseDto getById(Long id) {
        GiftCard giftCard = findByIdWithDetails(id);
        refreshExpiryStatus(giftCard);
        return toDto(giftCard, giftCardEventRepository.findByGiftCardIdOrderByCreatedAtDesc(giftCard.getId()), null);
    }

    public GiftCardResponseDto getByCodePublic(String code) {
        GiftCard giftCard = findByCodeWithDetails(code);
        refreshExpiryStatus(giftCard);
        return toDto(giftCard, giftCardEventRepository.findByGiftCardIdOrderByCreatedAtDesc(giftCard.getId()), null);
    }

    @Transactional
    public GiftCardResponseDto redeemInternal(
            Long id,
            GiftCardRedeemRequestDto request,
            Long actorUserId,
            String actorUsername,
            String ip,
            String userAgent
    ) {
        GiftCard giftCard = findByIdWithDetails(id);
        refreshExpiryStatus(giftCard);
        ensureRedeemable(giftCard, request.isAllowExpiredOverride(), request.getOverrideReason(), true);
        applyRedeem(giftCard, request.getItems(), request.getNote(), GiftCardAuditActor.ADMIN,
                actorUserId, actorUsername, ip, userAgent);

        if (giftCard.getStatus() == GiftCardStatus.EXPIRED && request.isAllowExpiredOverride()) {
            saveEvent(giftCard, null, GiftCardEventType.OVERRIDE_EXPIRED, null,
                    "Override canje vencido: " + request.getOverrideReason(),
                    GiftCardAuditActor.ADMIN, actorUserId, actorUsername, ip, userAgent, null);
        }

        return toDto(giftCard, giftCardEventRepository.findByGiftCardIdOrderByCreatedAtDesc(giftCard.getId()), null);
    }

    @Transactional
    public GiftCardResponseDto redeemPublic(
            String code,
            GiftCardRedeemRequestDto request,
            String ip,
            String userAgent
    ) {
        if (request.getPin() == null || request.getPin().isBlank()) {
            throw new ValidationException("PIN requerido para canje público");
        }

        GiftCard giftCard = findByCodeWithDetails(code);
        refreshExpiryStatus(giftCard);
        validatePinAndMaybeLock(giftCard, request.getPin());
        ensureRedeemable(giftCard, false, null, false);
        applyRedeem(giftCard, request.getItems(), request.getNote(), GiftCardAuditActor.PUBLIC,
                null, "PUBLIC", ip, userAgent);

        return toDto(giftCard, giftCardEventRepository.findByGiftCardIdOrderByCreatedAtDesc(giftCard.getId()), null);
    }

    @Transactional
    public GiftCardResponseDto revertRedeem(
            Long id,
            GiftCardRevertRequestDto request,
            Long actorUserId,
            String actorUsername,
            String ip,
            String userAgent
    ) {
        GiftCard giftCard = findByIdWithDetails(id);
        refreshExpiryStatus(giftCard);
        if (giftCard.getStatus() == GiftCardStatus.CANCELLED) {
            throw new ConflictException("No se puede revertir canje en giftcard anulada");
        }

        Map<Long, Integer> quantitiesByItem = aggregateByItemId(request.getItems());
        for (GiftCardItem item : giftCard.getItems()) {
            Integer quantityToRevert = quantitiesByItem.get(item.getId());
            if (quantityToRevert == null) continue;
            if (quantityToRevert <= 0) {
                throw new ValidationException("Cantidad inválida para revertir");
            }
            if (quantityToRevert > item.getRedeemedQuantity()) {
                throw new ValidationException("No puedes revertir más de lo canjeado en item " + item.getId());
            }

            item.setRedeemedQuantity(item.getRedeemedQuantity() - quantityToRevert);
            GiftCardEvent revertEvent = saveEvent(
                    giftCard, item, GiftCardEventType.REVERTED, quantityToRevert, request.getNote(),
                    GiftCardAuditActor.ADMIN, actorUserId, actorUsername, ip, userAgent, null
            );
            revertEvent.setRelatedEventId(null);
        }

        giftCard.setStatus(resolveStatus(giftCard));
        giftCard = giftCardRepository.save(giftCard);
        return toDto(giftCard, giftCardEventRepository.findByGiftCardIdOrderByCreatedAtDesc(giftCard.getId()), null);
    }

    @Transactional
    public GiftCardResponseDto cancel(
            Long id,
            String note,
            Long actorUserId,
            String actorUsername,
            String ip,
            String userAgent
    ) {
        GiftCard giftCard = findByIdWithDetails(id);
        refreshExpiryStatus(giftCard);
        int totalRedeemed = giftCard.getItems().stream().mapToInt(GiftCardItem::getRedeemedQuantity).sum();
        if (totalRedeemed > 0) {
            throw new ConflictException("No se puede anular GiftCard con canjes realizados");
        }

        giftCard.setStatus(GiftCardStatus.CANCELLED);
        giftCard.setCancelledAt(LocalDateTime.now(CHILE_ZONE_ID));
        giftCard = giftCardRepository.save(giftCard);
        saveEvent(giftCard, null, GiftCardEventType.CANCELLED, null,
                note == null || note.isBlank() ? "GiftCard anulada" : note,
                GiftCardAuditActor.ADMIN, actorUserId, actorUsername, ip, userAgent, null);
        return toDto(giftCard, giftCardEventRepository.findByGiftCardIdOrderByCreatedAtDesc(giftCard.getId()), null);
    }

    private void applyRedeem(
            GiftCard giftCard,
            List<GiftCardRedeemItemDto> items,
            String note,
            GiftCardAuditActor actor,
            Long actorUserId,
            String actorUsername,
            String ip,
            String userAgent
    ) {
        Map<Long, Integer> quantitiesByItem = aggregateByItemId(items);
        Set<Long> found = new HashSet<>();
        for (GiftCardItem item : giftCard.getItems()) {
            Integer quantityToRedeem = quantitiesByItem.get(item.getId());
            if (quantityToRedeem == null) continue;
            found.add(item.getId());
            int remaining = item.getQuantity() - item.getRedeemedQuantity();
            if (quantityToRedeem > remaining) {
                throw new ValidationException("No puedes canjear más de lo disponible en item " + item.getId());
            }

            item.setRedeemedQuantity(item.getRedeemedQuantity() + quantityToRedeem);
            saveEvent(giftCard, item, GiftCardEventType.REDEEMED, quantityToRedeem, note, actor,
                    actorUserId, actorUsername, ip, userAgent, null);
        }

        if (found.size() != quantitiesByItem.size()) {
            throw new ResourceNotFoundException("Uno o más giftCardItemId no pertenecen a la GiftCard");
        }

        giftCard.setStatus(resolveStatus(giftCard));
        giftCardRepository.save(giftCard);
    }

    private Map<Long, Integer> aggregateByItemId(List<GiftCardRedeemItemDto> items) {
        Map<Long, Integer> quantitiesByItem = new HashMap<>();
        for (GiftCardRedeemItemDto item : items) {
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new ValidationException("Cantidad inválida");
            }
            quantitiesByItem.merge(item.getGiftCardItemId(), item.getQuantity(), Integer::sum);
        }
        return quantitiesByItem;
    }

    private void ensureEditable(GiftCard giftCard) {
        if (giftCard.getStatus() != GiftCardStatus.ACTIVE) {
            throw new ConflictException("Solo se puede editar una GiftCard ACTIVA");
        }
        boolean hasRedeemed = giftCard.getItems().stream().anyMatch(item -> item.getRedeemedQuantity() > 0);
        if (hasRedeemed) {
            throw new ConflictException("No se puede editar una GiftCard con canjes");
        }
    }

    private void ensureRedeemable(
            GiftCard giftCard,
            boolean allowExpiredOverride,
            String overrideReason,
            boolean isAdmin
    ) {
        if (giftCard.getStatus() == GiftCardStatus.CANCELLED) {
            throw new ConflictException("GiftCard anulada");
        }
        if (giftCard.getStatus() == GiftCardStatus.REDEEMED) {
            throw new ConflictException("GiftCard ya fue canjeada completamente");
        }
        if (giftCard.getStatus() == GiftCardStatus.EXPIRED) {
            if (!isAdmin || !allowExpiredOverride) {
                throw new ConflictException("GiftCard vencida");
            }
            if (overrideReason == null || overrideReason.isBlank()) {
                throw new ValidationException("Motivo obligatorio para override de GiftCard vencida");
            }
        }
    }

    private void validatePinAndMaybeLock(GiftCard giftCard, String pin) {
        LocalDateTime now = LocalDateTime.now(CHILE_ZONE_ID);
        if (giftCard.getPinLockedUntil() != null && giftCard.getPinLockedUntil().isAfter(now)) {
            throw new ValidationException("PIN bloqueado temporalmente, intenta más tarde");
        }

        if (!passwordEncoder.matches(pin, giftCard.getPinHash())) {
            int attempts = (giftCard.getPinFailedAttempts() == null ? 0 : giftCard.getPinFailedAttempts()) + 1;
            giftCard.setPinFailedAttempts(attempts);
            if (attempts >= PIN_MAX_ATTEMPTS) {
                giftCard.setPinLockedUntil(now.plus(PIN_LOCK_DURATION));
                giftCard.setPinFailedAttempts(0);
            }
            giftCardRepository.save(giftCard);
            throw new ValidationException("PIN inválido");
        }

        giftCard.setPinFailedAttempts(0);
        giftCard.setPinLockedUntil(null);
        giftCardRepository.save(giftCard);
    }

    private GiftCardStatus resolveStatus(GiftCard giftCard) {
        if (giftCard.getStatus() == GiftCardStatus.CANCELLED) return GiftCardStatus.CANCELLED;
        LocalDate today = ZonedDateTime.now(CHILE_ZONE_ID).toLocalDate();
        if (giftCard.getExpiresOn().isBefore(today)) return GiftCardStatus.EXPIRED;

        int totalQty = giftCard.getItems().stream().mapToInt(GiftCardItem::getQuantity).sum();
        int redeemedQty = giftCard.getItems().stream().mapToInt(GiftCardItem::getRedeemedQuantity).sum();

        if (totalQty == 0 || redeemedQty == 0) return GiftCardStatus.ACTIVE;
        if (redeemedQty >= totalQty) return GiftCardStatus.REDEEMED;
        return GiftCardStatus.PARTIAL;
    }

    @Transactional
    public void refreshExpiryStatus(GiftCard giftCard) {
        GiftCardStatus resolved = resolveStatus(giftCard);
        if (resolved != giftCard.getStatus()) {
            giftCard.setStatus(resolved);
            giftCardRepository.save(giftCard);
        }
    }

    private GiftCardResponseDto toDto(GiftCard giftCard, List<GiftCardEvent> events, String plainPin) {
        List<GiftCardResponseDto.ItemDto> itemDtos = giftCard.getItems().stream()
                .map(item -> GiftCardResponseDto.ItemDto.builder()
                        .id(item.getId())
                        .serviceId(item.getService() != null ? item.getService().getId() : null)
                        .serviceName(item.getServiceNameSnapshot())
                        .unitPrice(item.getUnitPriceSnapshot())
                        .quantity(item.getQuantity())
                        .redeemedQuantity(item.getRedeemedQuantity())
                        .remainingQuantity(Math.max(0, item.getQuantity() - item.getRedeemedQuantity()))
                        .build())
                .toList();

        List<GiftCardResponseDto.EventDto> eventDtos = events == null ? null : events.stream()
                .map(event -> GiftCardResponseDto.EventDto.builder()
                        .id(event.getId())
                        .eventType(event.getEventType())
                        .giftCardItemId(event.getGiftCardItem() != null ? event.getGiftCardItem().getId() : null)
                        .quantity(event.getQuantity())
                        .note(event.getNote())
                        .actor(event.getActor())
                        .actorUserId(event.getActorUserId())
                        .actorUsername(event.getActorUsername())
                        .requestIp(event.getRequestIp())
                        .userAgent(event.getUserAgent())
                        .relatedEventId(event.getRelatedEventId())
                        .createdAt(event.getCreatedAt())
                        .build())
                .toList();

        return GiftCardResponseDto.builder()
                .id(giftCard.getId())
                .code(giftCard.getCode())
                .status(giftCard.getStatus())
                .beneficiaryName(giftCard.getBeneficiaryNameSnapshot())
                .beneficiaryPhone(giftCard.getBeneficiaryPhoneSnapshot())
                .beneficiaryEmail(giftCard.getBeneficiaryEmailSnapshot())
                .beneficiaryCustomerId(giftCard.getBeneficiaryCustomer() != null ? giftCard.getBeneficiaryCustomer().getId() : null)
                .buyerName(giftCard.getBuyerName())
                .buyerPhone(giftCard.getBuyerPhone())
                .buyerEmail(giftCard.getBuyerEmail())
                .expiresOn(giftCard.getExpiresOn())
                .issuedAt(giftCard.getIssuedAt())
                .totalAmount(giftCard.getTotalAmount())
                .paidAmount(giftCard.getPaidAmount())
                .paymentMethod(giftCard.getPaymentMethod())
                .publicUrl(giftCard.getPublicUrl())
                .items(itemDtos)
                .events(eventDtos)
                .plainPin(plainPin)
                .build();
    }

    private List<ItemBuildData> buildItemData(List<GiftCardItemRequestDto> requestItems) {
        List<Long> ids = requestItems.stream()
                .map(GiftCardItemRequestDto::getServiceId)
                .distinct()
                .toList();
        List<ServiceCatalog> services = serviceCatalogService.findByIds(ids);
        Map<Long, ServiceCatalog> serviceById = services.stream()
                .collect(Collectors.toMap(ServiceCatalog::getId, s -> s));

        List<ItemBuildData> itemData = new ArrayList<>();
        for (GiftCardItemRequestDto reqItem : requestItems) {
            ServiceCatalog service = serviceById.get(reqItem.getServiceId());
            if (service == null) {
                throw new ResourceNotFoundException("Servicio no encontrado: " + reqItem.getServiceId());
            }
            itemData.add(new ItemBuildData(service, service.getPrice().intValue(), reqItem.getQuantity()));
        }
        return mergeDuplicateServices(itemData);
    }

    private List<ItemBuildData> mergeDuplicateServices(List<ItemBuildData> itemData) {
        Map<Long, ItemBuildData> merged = new LinkedHashMap<>();
        for (ItemBuildData data : itemData) {
            ItemBuildData current = merged.get(data.service.getId());
            if (current == null) {
                merged.put(data.service.getId(), data);
            } else {
                current.quantity += data.quantity;
            }
        }
        return new ArrayList<>(merged.values());
    }

    private GiftCardEvent saveEvent(
            GiftCard giftCard,
            GiftCardItem item,
            GiftCardEventType type,
            Integer quantity,
            String note,
            GiftCardAuditActor actor,
            Long actorUserId,
            String actorUsername,
            String ip,
            String userAgent,
            Long relatedEventId
    ) {
        GiftCardEvent event = GiftCardEvent.builder()
                .giftCard(giftCard)
                .giftCardItem(item)
                .eventType(type)
                .quantity(quantity)
                .note(note)
                .actor(actor)
                .actorUserId(actorUserId)
                .actorUsername(actorUsername)
                .requestIp(ip)
                .userAgent(userAgent)
                .relatedEventId(relatedEventId)
                .build();
        return giftCardEventRepository.save(event);
    }

    private Customer findOrCreateBeneficiary(String fullName, String phone, String email) {
        return customerService.findByPhone(phone)
                .orElseGet(() -> createBeneficiaryWithOptionalEmailFallback(fullName, phone, email));
    }

    private Customer createBeneficiaryWithOptionalEmailFallback(String fullName, String phone, String email) {
        String normalizedEmail = normalizeNullable(email, null);
        try {
            return customerService.create(CustomerDto.builder()
                    .fullName(fullName.trim())
                    .phone(phone.trim())
                    .email(normalizedEmail)
                    .notificationPreference(NotificationPreference.BOTH)
                    .build());
        } catch (IllegalArgumentException ex) {
            // Email is optional for beneficiary. If the email already exists in another customer,
            // create the beneficiary without email instead of failing the giftcard creation.
            if (normalizedEmail != null && ex.getMessage() != null && ex.getMessage().contains("Ya existe un cliente con el email")) {
                return customerService.create(CustomerDto.builder()
                        .fullName(fullName.trim())
                        .phone(phone.trim())
                        .email(null)
                        .notificationPreference(NotificationPreference.BOTH)
                        .build());
            }
            throw ex;
        }
    }

    private void validateDates(LocalDate expiresOn) {
        LocalDate today = ZonedDateTime.now(CHILE_ZONE_ID).toLocalDate();
        if (expiresOn.isBefore(today)) {
            throw new ValidationException("La fecha de vencimiento no puede ser anterior a hoy");
        }
    }

    private String normalizeNullable(String raw, String fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        return raw.trim();
    }

    private GiftCard findByIdWithDetails(Long id) {
        return giftCardRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("GiftCard no encontrada"));
    }

    private GiftCard findByCodeWithDetails(String code) {
        return giftCardRepository.findByCodeWithDetails(code)
                .orElseThrow(() -> new ResourceNotFoundException("GiftCard no encontrada"));
    }

    private String generateCode(Long id) {
        int year = Year.now(CHILE_ZONE_ID).getValue();
        return String.format("GC-%d-%06d", year, id);
    }

    private String generatePin() {
        SecureRandom random = new SecureRandom();
        int number = random.nextInt(1_000_000);
        return String.format("%06d", number);
    }

    private String buildPublicUrl(String code) {
        String base = frontendBaseUrl == null ? "" : frontendBaseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/giftcards/public/" + code;
    }

    public Long resolveCurrentUserIdOrNull(String username) {
        if (username == null || username.isBlank() || "anonymousUser".equals(username)) {
            return null;
        }
        return userRepository.findByUsername(username).map(User::getId).orElse(null);
    }

    private static class ItemBuildData {
        private final ServiceCatalog service;
        private final int unitPrice;
        private int quantity;

        private ItemBuildData(ServiceCatalog service, int unitPrice, int quantity) {
            this.service = service;
            this.unitPrice = unitPrice;
            this.quantity = quantity;
        }
    }
}
