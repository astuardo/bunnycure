package cl.bunnycure.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Texto de búsqueda de clientas: sin tildes, sin signos y por palabras")
class CustomerSearchTextTest {

    @Test
    void normalizesNamePhoneRutAndEmail() {
        Customer customer = new Customer("María José Pérez", "+56970000005", "Maria.Perez@Mail.cl", "15.007.919-K");

        assertEquals("maria jose perez 56970000005 15007919k mariaperezmailcl", CustomerSearchText.of(customer));
    }

    @Test
    void splitsQueryIntoFourNormalizedTerms() {
        assertEquals(List.of("soto", "camila", "", ""), CustomerSearchText.terms("  Soto   CAMILA "));
        assertEquals(List.of("15007919", "", "", ""), CustomerSearchText.terms("15.007.919"));
        assertEquals(List.of("970000005", "", "", ""), CustomerSearchText.terms("9 7000 0005"));
        assertEquals(List.of("camila", "70000005", "", ""), CustomerSearchText.terms("Camila 70000005"));
    }

    @Test
    void extraWordsAreSearchedTogetherInTheLastTerm() {
        assertEquals(List.of("a", "b", "c", "d%e"), CustomerSearchText.terms("a b c d e"));
    }

    @Test
    void blankOrSymbolOnlyQueryHasNoTerms() {
        assertEquals(List.of("", "", "", ""), CustomerSearchText.terms(" .-+ "));
        assertEquals(List.of("", "", "", ""), CustomerSearchText.terms(null));
    }
}
