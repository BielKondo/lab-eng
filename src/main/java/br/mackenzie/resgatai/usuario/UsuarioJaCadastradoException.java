package br.mackenzie.resgatai.usuario;

/**
 * UC01, exceção 6b: já existe usuário com o mesmo CNPJ/CPF ou e-mail.
 * Apresenta o registro existente para o administrador atualizá-lo (fluxo 3b) ou cancelar.
 */
public class UsuarioJaCadastradoException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final transient Usuario usuarioExistente;
    private final String campo;

    public UsuarioJaCadastradoException(String campo, Usuario usuarioExistente) {
        super("Já existe um usuário cadastrado com o mesmo " + campo + ": "
                + usuarioExistente.getNome() + " (" + usuarioExistente.getDocumento() + ").");
        this.campo = campo;
        this.usuarioExistente = usuarioExistente;
    }

    public Usuario getUsuarioExistente() { return usuarioExistente; }

    /** "CNPJ/CPF" ou "e-mail". */
    public String getCampo() { return campo; }
}
