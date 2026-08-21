package cl.bunnycure.service;

import cl.bunnycure.domain.enums.AppointmentStatus;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.ServiceCatalog;
import cl.bunnycure.domain.repository.AppointmentRepository;
import cl.bunnycure.web.dto.DashboardStatsDto;
import cl.bunnycure.web.dto.SpecialistStatsDto;
import cl.bunnycure.web.dto.TodayOperationalStatsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsService {

    private final AppointmentRepository appointmentRepository;
    private final AppSettingsService appSettingsService;

    @Transactional(readOnly = true)
    public DashboardStatsDto getDashboardStats() {
        LocalDate now = getTodayInZone();
        LocalDate startOfMonth = now.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate endOfMonth = now.with(TemporalAdjusters.lastDayOfMonth());

        List<Appointment> monthAppointments = appointmentRepository.findByDateRangeWithCustomer(startOfMonth, endOfMonth);
        
        // Filtrar solo las que no están canceladas
        List<Appointment> activeAppointments = monthAppointments.stream()
                .filter(a -> a.getStatus() != AppointmentStatus.CANCELLED)
                .toList();

        BigDecimal projectedRevenue = BigDecimal.ZERO;
        BigDecimal completedRevenue = BigDecimal.ZERO;
        long completedCount = 0;
        long pendingOrConfirmedCount = 0;

        Map<Long, DashboardStatsDto.ServiceStatDto> serviceStatsMap = new HashMap<>();
        Map<Long, DashboardStatsDto.CustomerStatDto> customerStatsMap = new HashMap<>();

        for (Appointment apt : activeAppointments) {
            BigDecimal aptTotal = calculateAppointmentTotal(apt);
            projectedRevenue = projectedRevenue.add(aptTotal);

            if (apt.getStatus() == AppointmentStatus.COMPLETED) {
                completedRevenue = completedRevenue.add(aptTotal);
                completedCount++;
            } else {
                pendingOrConfirmedCount++;
            }

            // Estadísticas por cliente
            Long customerId = apt.getCustomer().getId();
            DashboardStatsDto.CustomerStatDto cStat = customerStatsMap.getOrDefault(customerId, 
                DashboardStatsDto.CustomerStatDto.builder()
                    .name(apt.getCustomer().getFullName())
                    .appointmentCount(0L)
                    .totalSpent(BigDecimal.ZERO)
                    .build());
            cStat.setAppointmentCount(cStat.getAppointmentCount() + 1);
            cStat.setTotalSpent(cStat.getTotalSpent().add(aptTotal));
            customerStatsMap.put(customerId, cStat);

            // Estadísticas por servicio
            List<ServiceCatalog> services = getAppointmentServices(apt);
            for (ServiceCatalog s : services) {
                DashboardStatsDto.ServiceStatDto sStat = serviceStatsMap.getOrDefault(s.getId(),
                    DashboardStatsDto.ServiceStatDto.builder()
                        .name(s.getName())
                        .count(0L)
                        .revenue(BigDecimal.ZERO)
                        .build());
                sStat.setCount(sStat.getCount() + 1);
                sStat.setRevenue(sStat.getRevenue().add(s.getPrice() != null ? s.getPrice() : BigDecimal.ZERO));
                serviceStatsMap.put(s.getId(), sStat);
            }
        }

        // Top 5 servicios más usados
        List<DashboardStatsDto.ServiceStatDto> topServices = serviceStatsMap.values().stream()
                .sorted(Comparator.comparing(DashboardStatsDto.ServiceStatDto::getCount).reversed())
                .limit(5)
                .collect(Collectors.toList());

        // Cliente que más gasta/asiste
        DashboardStatsDto.CustomerStatDto topCustomer = customerStatsMap.values().stream()
                .max(Comparator.comparing(DashboardStatsDto.CustomerStatDto::getAppointmentCount)
                        .thenComparing(DashboardStatsDto.CustomerStatDto::getTotalSpent))
                .orElse(null);

        return DashboardStatsDto.builder()
                .totalRevenueMonth(projectedRevenue)
                .completedRevenueMonth(completedRevenue)
                .projectedRevenueMonth(projectedRevenue)
                .totalAppointmentsMonth((long) activeAppointments.size())
                .completedAppointmentsMonth(completedCount)
                .pendingOrConfirmedAppointmentsMonth(pendingOrConfirmedCount)
                .topServices(topServices)
                .topCustomer(topCustomer)
                .build();
    }

    @Transactional(readOnly = true)
    public TodayOperationalStatsDto getTodayOperationalStats() {
        ZonedDateTime nowInZone = getNowInZone();
        LocalDate today = nowInZone.toLocalDate();
        LocalTime nowTime = nowInZone.toLocalTime();
        LocalTime in2HoursTime = nowTime.plusHours(2);

        List<Appointment> todayAppointments = appointmentRepository.findByDateWithCustomer(today);

        long totalAppointments = todayAppointments.size();
        long completedCount = 0;
        long pendingCount = 0;
        long confirmedCount = 0;
        long cancelledCount = 0;
        long upcoming2HoursCount = 0;
        long potentialNoShowCount = 0;

        BigDecimal collectedRevenue = BigDecimal.ZERO;
        BigDecimal projectedRevenue = BigDecimal.ZERO;

        Appointment nextAppointment = null;

        for (Appointment apt : todayAppointments) {
            BigDecimal aptTotal = calculateAppointmentTotal(apt);
            AppointmentStatus status = apt.getStatus();

            if (status != AppointmentStatus.CANCELLED) {
                projectedRevenue = projectedRevenue.add(aptTotal);
            }

            switch (status) {
                case COMPLETED -> {
                    completedCount++;
                    collectedRevenue = collectedRevenue.add(aptTotal);
                }
                case PENDING -> pendingCount++;
                case CONFIRMED -> confirmedCount++;
                case CANCELLED -> cancelledCount++;
            }

            // Detección operacional en tiempo real
            if (status == AppointmentStatus.PENDING || status == AppointmentStatus.CONFIRMED) {
                LocalTime aptTime = apt.getAppointmentTime();
                if (aptTime != null) {
                    // Si ya pasó la hora y sigue sin completarse/cancelarse
                    if (aptTime.isBefore(nowTime.minusMinutes(15))) {
                        potentialNoShowCount++;
                    }

                    // En ventana de las próximas 2 horas
                    if (!aptTime.isBefore(nowTime) && aptTime.isBefore(in2HoursTime)) {
                        upcoming2HoursCount++;
                    }

                    // Identificar la próxima cita más inmediata
                    if (aptTime.isAfter(nowTime) || aptTime.equals(nowTime)) {
                        if (nextAppointment == null || aptTime.isBefore(nextAppointment.getAppointmentTime())) {
                            nextAppointment = apt;
                        }
                    }
                }
            }
        }

        long activeCount = totalAppointments - cancelledCount;
        int completionRate = activeCount > 0 ? (int) Math.round(((double) completedCount / activeCount) * 100) : 0;

        String nextCustomerName = nextAppointment != null && nextAppointment.getCustomer() != null
                ? nextAppointment.getCustomer().getFullName() : null;
        String nextServiceName = null;
        if (nextAppointment != null) {
            List<ServiceCatalog> services = getAppointmentServices(nextAppointment);
            nextServiceName = !services.isEmpty() ? services.get(0).getName()
                    : (nextAppointment.getService() != null ? nextAppointment.getService().getName() : null);
        }
        String nextSpecialistName = nextAppointment != null && nextAppointment.getSpecialist() != null
                ? nextAppointment.getSpecialist().getFullName() : null;
        LocalTime nextAppointmentTime = nextAppointment != null ? nextAppointment.getAppointmentTime() : null;

        return TodayOperationalStatsDto.builder()
                .date(today)
                .totalAppointments(totalAppointments)
                .completedCount(completedCount)
                .pendingCount(pendingCount)
                .confirmedCount(confirmedCount)
                .cancelledCount(cancelledCount)
                .inProgressOrUpcoming2HoursCount(upcoming2HoursCount)
                .potentialNoShowCount(potentialNoShowCount)
                .collectedRevenue(collectedRevenue)
                .projectedRevenue(projectedRevenue)
                .completionRate(completionRate)
                .nextAppointmentTime(nextAppointmentTime)
                .nextCustomerName(nextCustomerName)
                .nextServiceName(nextServiceName)
                .nextSpecialistName(nextSpecialistName)
                .build();
    }

    @Transactional(readOnly = true)
    public List<SpecialistStatsDto> getSpecialistStats(LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : getTodayInZone().with(TemporalAdjusters.firstDayOfMonth());
        LocalDate end = endDate != null ? endDate : getTodayInZone().with(TemporalAdjusters.lastDayOfMonth());

        List<Appointment> appointments = appointmentRepository.findByDateRangeWithCustomer(start, end);

        Map<String, SpecialistStatsAccumulator> map = new HashMap<>();

        for (Appointment apt : appointments) {
            String specName = apt.getSpecialist() != null && apt.getSpecialist().getFullName() != null
                    ? apt.getSpecialist().getFullName()
                    : "Sin asignar / General";
            Long specId = apt.getSpecialist() != null ? apt.getSpecialist().getId() : null;

            SpecialistStatsAccumulator acc = map.computeIfAbsent(specName, k -> new SpecialistStatsAccumulator(specId, specName));
            acc.totalCount++;

            BigDecimal total = calculateAppointmentTotal(apt);

            if (apt.getStatus() == AppointmentStatus.COMPLETED) {
                acc.completedCount++;
                acc.revenue = acc.revenue.add(total);
            } else if (apt.getStatus() == AppointmentStatus.CANCELLED) {
                acc.cancelledCount++;
            }
        }

        return map.values().stream()
                .map(acc -> {
                    long activeCount = acc.totalCount - acc.cancelledCount;
                    int completionRate = activeCount > 0 ? (int) Math.round(((double) acc.completedCount / activeCount) * 100) : 0;
                    return SpecialistStatsDto.builder()
                            .specialistId(acc.specialistId)
                            .specialistName(acc.specialistName)
                            .totalCount(acc.totalCount)
                            .completedCount(acc.completedCount)
                            .cancelledCount(acc.cancelledCount)
                            .revenue(acc.revenue)
                            .completionRate(completionRate)
                            .build();
                })
                .sorted(Comparator.comparing(SpecialistStatsDto::getCompletedCount).reversed()
                        .thenComparing(SpecialistStatsDto::getRevenue, Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    private static class SpecialistStatsAccumulator {
        Long specialistId;
        String specialistName;
        long totalCount = 0;
        long completedCount = 0;
        long cancelledCount = 0;
        BigDecimal revenue = BigDecimal.ZERO;

        SpecialistStatsAccumulator(Long specialistId, String specialistName) {
            this.specialistId = specialistId;
            this.specialistName = specialistName;
        }
    }

    private BigDecimal calculateAppointmentTotal(Appointment apt) {
        if (apt.getServices() != null && !apt.getServices().isEmpty()) {
            return apt.getServices().stream()
                    .map(s -> s.getPrice() != null ? s.getPrice() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        return apt.getService() != null && apt.getService().getPrice() != null
                ? apt.getService().getPrice() : BigDecimal.ZERO;
    }

    private List<ServiceCatalog> getAppointmentServices(Appointment apt) {
        if (apt.getServices() != null && !apt.getServices().isEmpty()) {
            return apt.getServices();
        }
        return apt.getService() != null ? List.of(apt.getService()) : Collections.emptyList();
    }

    private LocalDate getTodayInZone() {
        return getNowInZone().toLocalDate();
    }

    private ZonedDateTime getNowInZone() {
        String timezone = appSettingsService.getAppTimezone();
        try {
            return ZonedDateTime.now(ZoneId.of(timezone));
        } catch (Exception ex) {
            log.warn("[STATS] Timezone invalida '{}'. Fallback America/Santiago", timezone);
            return ZonedDateTime.now(ZoneId.of("America/Santiago"));
        }
    }
}

