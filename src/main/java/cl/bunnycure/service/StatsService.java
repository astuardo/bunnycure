package cl.bunnycure.service;

import cl.bunnycure.domain.enums.AppointmentStatus;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.ServiceCatalog;
import cl.bunnycure.domain.repository.AppointmentRepository;
import cl.bunnycure.web.dto.DashboardStatsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsService {

    private final AppointmentRepository appointmentRepository;

    @Transactional(readOnly = true)
    public DashboardStatsDto getDashboardStats() {
        LocalDate now = LocalDate.now();
        LocalDate startOfMonth = now.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate endOfMonth = now.with(TemporalAdjusters.lastDayOfMonth());

        List<Appointment> monthAppointments = appointmentRepository.findByDateRangeWithCustomer(startOfMonth, endOfMonth);
        
        // Filtrar solo las que no están canceladas para ingresos
        List<Appointment> activeAppointments = monthAppointments.stream()
                .filter(a -> a.getStatus() != AppointmentStatus.CANCELLED)
                .toList();

        BigDecimal totalRevenue = BigDecimal.ZERO;
        Map<Long, DashboardStatsDto.ServiceStatDto> serviceStatsMap = new HashMap<>();
        Map<Long, DashboardStatsDto.CustomerStatDto> customerStatsMap = new HashMap<>();

        for (Appointment apt : activeAppointments) {
            BigDecimal aptTotal = calculateAppointmentTotal(apt);
            totalRevenue = totalRevenue.add(aptTotal);

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
                sStat.setRevenue(sStat.getRevenue().add(s.getPrice()));
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
                .totalRevenueMonth(totalRevenue)
                .totalAppointmentsMonth((long) activeAppointments.size())
                .topServices(topServices)
                .topCustomer(topCustomer)
                .build();
    }

    private BigDecimal calculateAppointmentTotal(Appointment apt) {
        if (apt.getServices() != null && !apt.getServices().isEmpty()) {
            return apt.getServices().stream()
                    .map(ServiceCatalog::getPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        return apt.getService() != null ? apt.getService().getPrice() : BigDecimal.ZERO;
    }

    private List<ServiceCatalog> getAppointmentServices(Appointment apt) {
        if (apt.getServices() != null && !apt.getServices().isEmpty()) {
            return apt.getServices();
        }
        return apt.getService() != null ? List.of(apt.getService()) : Collections.emptyList();
    }
}
