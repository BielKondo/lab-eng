package br.mackenzie.resgatai.comum;

/** Pré-condição de perfil não atendida (ex.: só Administrador gerencia usuários, só Doador cadastra lotes). HTTP 403. */
public class AcessoNegadoException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public AcessoNegadoException(String mensagem) {
        super(mensagem);
    }
}
