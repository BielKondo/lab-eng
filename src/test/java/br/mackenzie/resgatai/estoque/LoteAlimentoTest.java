package br.mackenzie.resgatai.estoque;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.Test;

public class LoteAlimentoTest {

    // Tabela de transições da seção 9 do CONTEXTO_PROJETO.md
    private static final Set<String> PERMITIDAS = Set.of(
            "DISPONIVEL->RESERVADO_PENDENTE",
            "DISPONIVEL->EXPIRADO",
            "RESERVADO_PENDENTE->COLETADO",
            "RESERVADO_PENDENTE->DISPONIVEL",
            "RESERVADO_PENDENTE->CANCELADO");

    @Test
    public void testTransicoesSeguemMaquinaDeEstados() {
        for (StatusLote origem : StatusLote.values()) {
            for (StatusLote destino : StatusLote.values()) {
                String transicao = origem + "->" + destino;
                assertEquals(PERMITIDAS.contains(transicao), origem.podeIrPara(destino), transicao);
            }
        }
    }

    @Test
    public void testAlterarStatusRejeitaTransicaoInvalida() {
        LoteAlimento lote = new LoteAlimento();
        assertEquals(StatusLote.DISPONIVEL, lote.getStatus());

        assertThrows(IllegalStateException.class, () -> lote.alterarStatus(StatusLote.COLETADO));
        assertEquals(StatusLote.DISPONIVEL, lote.getStatus());

        lote.alterarStatus(StatusLote.RESERVADO_PENDENTE);
        assertEquals(StatusLote.RESERVADO_PENDENTE, lote.getStatus());
    }
}
