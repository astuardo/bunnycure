package cl.bunnycure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PiiMaskingConverter: enmascarado de datos personales en logs")
class PiiMaskingConverterTest {

    @Test
    @DisplayName("Enmascara emails conservando la inicial y el dominio")
    void masksEmails() {
        assertThat(PiiMaskingConverter.mask("[MAIL-OK] Recordatorio → valentina.flores@test.cl"))
                .isEqualTo("[MAIL-OK] Recordatorio → v***@test.cl");
    }

    @Test
    @DisplayName("Enmascara teléfonos chilenos en sus formatos habituales dejando 4 dígitos")
    void masksPhones() {
        assertThat(PiiMaskingConverter.mask("enviado a +56977665544")).isEqualTo("enviado a +*******5544");
        assertThat(PiiMaskingConverter.mask("enviado a 56977665544.")).isEqualTo("enviado a *******5544.");
        assertThat(PiiMaskingConverter.mask("phone=977665544")).isEqualTo("phone=*****5544");
        assertThat(PiiMaskingConverter.mask("tel +56 9 7766 5544 ok")).isEqualTo("tel +** * **** 5544 ok");
    }

    @Test
    @DisplayName("Enmascara varios datos en un mismo mensaje")
    void masksMultipleValues() {
        assertThat(PiiMaskingConverter.mask("Cliente: Ana, Email: ana@x.cl, Phone: +56911223344"))
                .isEqualTo("Cliente: Ana, Email: a***@x.cl, Phone: +*******3344");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Fecha 2026-10-16T15:02:11.545-03:00",
            "IP 186.10.104.109",
            "appointment 12345 customer 678",
            "WAMID: wamid.HBgLNTY5Nzc2NjU1NDQVAgARGBI5",
            "request_id=4cf04958-68bb-adf8-f203-dd60b09b2d96",
            "Total $45.000 CLP",
            "Payload rejected by size limit: 1048576 bytes"
    })
    @DisplayName("No altera fechas, IPs, IDs cortos, WAMIDs, UUIDs ni montos")
    void leavesNonPiiUntouched(String message) {
        assertThat(PiiMaskingConverter.mask(message)).isEqualTo(message);
    }
}
