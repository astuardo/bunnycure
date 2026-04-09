package cl.bunnycure.web.dto;

import cl.bunnycure.domain.enums.AppointmentStatus;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppointmentDto {

    private Long id;

    @NotNull(message = "El cliente es obligatorio")
    private Long customerId;

    @NotNull(message = "El servicio principal es obligatorio")
    private Long serviceId;

    private List<Long> serviceIds;

    @NotNull(message = "La fecha es obligatoria")
    private LocalDate appointmentDate;

    @NotNull(message = "La hora es obligatoria")
    private LocalTime appointmentTime;

    @Builder.Default
    private AppointmentStatus status = AppointmentStatus.PENDING;

    private String observations;
}
