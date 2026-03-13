package cl.bunnycure.web.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

public class BookingApprovalDto {

    @NotNull(message = "La fecha de la cita es obligatoria")
    private LocalDate appointmentDate;

    @NotNull(message = "La hora de la cita es obligatoria")
    private LocalTime appointmentTime;

    // Permite cambiar el servicio al aprobar (ej: la clienta pidió X pero se hará Y)
    private Long serviceId;

    private String adminNotes;

    // Getters & Setters
    public LocalDate getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDate appointmentDate) { this.appointmentDate = appointmentDate; }
    public LocalTime getAppointmentTime() { return appointmentTime; }
    public void setAppointmentTime(LocalTime appointmentTime) { this.appointmentTime = appointmentTime; }
    public Long getServiceId() { return serviceId; }
    public void setServiceId(Long serviceId) { this.serviceId = serviceId; }
    public String getAdminNotes() { return adminNotes; }
    public void setAdminNotes(String adminNotes) { this.adminNotes = adminNotes; }
}