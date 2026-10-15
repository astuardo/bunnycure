package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.enums.AppointmentStatus;
import cl.bunnycure.domain.model.Appointment;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class AppointmentSpecifications {

    private AppointmentSpecifications() {}

    public static Specification<Appointment> filter(
            Long customerId,
            AppointmentStatus status,
            Long specialistId,
            LocalDate startDate,
            LocalDate endDate) {
        return (root, query, cb) -> {
            // Apply fetch joins only on entity queries, not on count queries
            if (query != null && Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("customer", JoinType.INNER);
                root.fetch("service", JoinType.INNER);
                root.fetch("specialist", JoinType.LEFT);
            }
            List<Predicate> predicates = new ArrayList<>();
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customer").get("id"), customerId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
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
