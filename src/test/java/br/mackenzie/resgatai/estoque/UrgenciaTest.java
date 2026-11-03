package br.mackenzie.resgatai.estoque;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** UC02, passo 6: faixas do indicador de urgência. */
public class UrgenciaTest {

    @Test
    public void testFaixasDeUrgencia() {
        assertEquals(Urgencia.VENCIDO, Urgencia.de(Duration.ofMinutes(-1)));
        assertEquals(Urgencia.VENCIDO, Urgencia.de(Duration.ZERO));
        assertEquals(Urgencia.CRITICA, Urgencia.de(Duration.ofMinutes(1)));
        assertEquals(Urgencia.CRITICA, Urgencia.de(Duration.ofHours(4)));
        assertEquals(Urgencia.ALTA, Urgencia.de(Duration.ofHours(4).plusSeconds(1)));
        assertEquals(Urgencia.ALTA, Urgencia.de(Duration.ofHours(12)));
        assertEquals(Urgencia.MEDIA, Urgencia.de(Duration.ofHours(12).plusSeconds(1)));
        assertEquals(Urgencia.MEDIA, Urgencia.de(Duration.ofHours(24)));
        assertEquals(Urgencia.BAIXA, Urgencia.de(Duration.ofHours(24).plusSeconds(1)));
    }
}
