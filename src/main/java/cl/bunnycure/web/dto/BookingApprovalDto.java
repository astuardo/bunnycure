package cl.bunnycure.web.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
public class BookingApprovalDto {

    @NotNull(message = "La fecha de la cita es obligatoria")
    @FutureOrPresent(message = "La fecha de la cita debe ser hoy o posterior")
    private LocalDate appointmentDate;

    @NotNull(message = "La hora de la cita es obligatoria")
    private LocalTime appointmentTime;

    // Permite cambiar el servicio al aprobar (ej: la clienta pidió X pero se hará Y)
    private Long serviceId;

    private String adminNotes;

}