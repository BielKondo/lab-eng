package br.mackenzie.resgatai.estoque;

import java.time.Duration;

/**
 * Indicador de urgência apresentado ao Doador no cadastro (UC02, passo 6),
 * calculado a partir do prazo restante até o vencimento.
 */
public enum Urgencia {
    CRITICA("Vence em até 4 horas"),
    ALTA("Vence em até 12 horas"),
    MEDIA("Vence em até 24 horas"),
    BAIXA("Vence em mais de 24 horas"),
    VENCIDO("Data de validade já passou");

    private final String descricao;

    Urgencia(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    public static Urgencia de(Duration prazoRestante) {
        if (prazoRestante.isNegative() || prazoRestante.isZero()) return VENCIDO;
        if (prazoRestante.compareTo(Duration.ofHours(4)) <= 0) return CRITICA;
        if (prazoRestante.compareTo(Duration.ofHours(12)) <= 0) return ALTA;
        if (prazoRestante.compareTo(Duration.ofHours(24)) <= 0) return MEDIA;
        return BAIXA;
    }
}
