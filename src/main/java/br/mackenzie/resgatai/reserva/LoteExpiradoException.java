package br.mackenzie.resgatai.reserva;

import br.mackenzie.resgatai.estoque.LoteAlimento;

/**
 * UC04, exceção 6b: a validade ou a janela de retirada do lote expirou.
 * O lote passa a EXPIRADO (gravado mesmo com a exceção), sai das listagens e o Doador é notificado.
 */
public class LoteExpiradoException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private final transient LoteAlimento lote;

    public LoteExpiradoException(LoteAlimento lote) {
        super("O lote " + lote.getNome() + " venceu ou saiu da janela de retirada e não está mais apto à reserva.");
        this.lote = lote;
    }

    public LoteAlimento getLote() { return lote; }
}
