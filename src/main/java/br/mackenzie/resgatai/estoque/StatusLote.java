package br.mackenzie.resgatai.estoque;

/** Máquina de estados do lote (seção 9 do CONTEXTO_PROJETO.md). */
public enum StatusLote {
    DISPONIVEL, RESERVADO_PENDENTE, COLETADO, EXPIRADO, CANCELADO;

    public boolean podeIrPara(StatusLote destino) {
        return switch (this) {
            case DISPONIVEL -> destino == RESERVADO_PENDENTE  // UC04
                    || destino == EXPIRADO;                   // UC04-6b / RNF02
            case RESERVADO_PENDENTE -> destino == COLETADO    // UC06
                    || destino == DISPONIVEL                  // UC05
                    || destino == CANCELADO;                  // UC06-7a
            case COLETADO, EXPIRADO, CANCELADO -> false;
        };
    }
}
