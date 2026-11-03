package br.mackenzie.resgatai.reserva;

import br.mackenzie.resgatai.estoque.LoteAlimento;
import br.mackenzie.resgatai.estoque.LoteAlimentoService;
import java.util.List;

/**
 * UC04, exceção 6a: o lote já foi reservado por outra instituição (ou saiu de DISPONIVEL).
 * A transação é desfeita e o bloqueio liberado sem alterar o registro; a exceção traz
 * os demais lotes disponíveis compatíveis com o Receptor (passo 3 do 6a).
 */
public class LoteIndisponivelException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private final transient LoteAlimento lote;
    private final transient List<LoteAlimentoService.Encontrado> alternativas;

    public LoteIndisponivelException(LoteAlimento lote, List<LoteAlimentoService.Encontrado> alternativas) {
        super("O lote " + lote.getNome() + " " + motivo(lote) + " e não está mais disponível.");
        this.lote = lote;
        this.alternativas = List.copyOf(alternativas);
    }

    private static String motivo(LoteAlimento lote) {
        return switch (lote.getStatus()) {
            case RESERVADO_PENDENTE -> "foi reservado por outra instituição";
            case COLETADO -> "já foi retirado";
            case EXPIRADO -> "venceu ou saiu da janela de retirada";
            case CANCELADO -> "foi cancelado pelo doador";
            case DISPONIVEL -> "não pôde ser reservado";
        };
    }

    public LoteAlimento getLote() { return lote; }

    /** Outros lotes disponíveis para o mesmo Receptor. */
    public List<LoteAlimentoService.Encontrado> getAlternativas() { return alternativas; }
}
