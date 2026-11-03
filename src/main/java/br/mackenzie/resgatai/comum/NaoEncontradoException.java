package br.mackenzie.resgatai.comum;

/** Recurso (usuário, lote, reserva, notificação) inexistente. HTTP 404. */
public class NaoEncontradoException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public NaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
