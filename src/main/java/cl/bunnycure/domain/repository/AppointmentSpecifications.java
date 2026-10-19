package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.enums.AppointmentStatus;
import cl.bunnycure.domain.model.Appointment;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class AppointmentSpecifications {

    private AppointmentSpecifications() {}

    public static Specification<Appointment> filter(
            Long customerId,
            AppointmentStatus status,
            Long specialistId,
            LocalDate startDate,
            LocalDate endDate) {
        return filter(customerId, status == null ? null : List.of(status), specialistId, startDate, endDate, null);
    }

    /**
     * @param statuses    estados permitidos (null o vacío = todos)
     * @param nearestFrom si no es null, ordena por cercanía a esa fecha: primero las citas desde esa
     *                    fecha en adelante (ascendente) y luego las anteriores (descendente). Solo se
     *                    aplica cuando el Pageable no trae orden propio (Spring Data no sobrescribe orderBy).
     */
    public static Specification<Appointment> filter(
            Long customerId,
            Collection<AppointmentStatus> statuses,
            Long specialistId,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate nearestFrom) {
        return (root, query, cb) -> {
            // Apply fetch joins only on entity queries, not on count queries
            boolean isCountQuery = query == null || Long.class == query.getResultType() || long.class == query.getResultType();
            if (!isCountQuery) {
                root.fetch("customer", JoinType.INNER);
                root.fetch("service", JoinType.INNER);
                root.fetch("specialist", JoinType.LEFT);

                if (nearestFrom != null) {
                    Expression<LocalDate> date = root.get("appointmentDate");
                    Expression<LocalTime> time = root.get("appointmentTime");
                    Predicate upcoming = cb.greaterThanOrEqualTo(date, nearestFrom);
                    query.orderBy(
                            // 0 = próximas, 1 = pasadas
                            cb.asc(cb.<Integer>selectCase().when(upcoming, 0).otherwise(1)),
                            cb.asc(cb.<LocalDate>selectCase().when(upcoming, date).otherwise(cb.nullLiteral(LocalDate.class))),
                            cb.asc(cb.<LocalTime>selectCase().when(upcoming, time).otherwise(cb.nullLiteral(LocalTime.class))),
                            cb.desc(date),
                            cb.desc(time));
                }
            }
            List<Predicate> predicates = new ArrayList<>();
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customer").get("id"), customerId));
            }
            if (statuses != null && !statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            }
            if (specialistId != null) {
                predicates.add(cb.equal(root.get("specialist").get("id"), specialistId));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("appointmentDate"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("appointmentDate"), endDate));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
