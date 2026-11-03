package br.mackenzie.resgatai.usuario;

/**
 * Formulário do UC01: identificação, contato, endereço e tipo de perfil (passo 4).
 * Também é devolvido pré-preenchido na atualização (fluxo 3b, passo 2).
 * Telefone e endereço são obrigatórios para Doador e Receptor.
 */
public record DadosUsuario(Perfil perfil,
                           String documento, // CPF ou CNPJ
                           String nome,
                           String email,
                           String telefone,
                           String endereco,
                           Double latitude,   // coordenadas do endereço (opcionais; UC03/UC04)
                           Double longitude) {

    /** Sem coordenadas — o endereço fica fora do cálculo de distância até ser geocodificado. */
    public DadosUsuario(Perfil perfil, String documento, String nome, String email, String telefone, String endereco) {
        this(perfil, documento, nome, email, telefone, endereco, null, null);
    }

    static DadosUsuario de(Usuario u) {
        return new DadosUsuario(u.getPerfil(), u.getDocumento(), u.getNome(), u.getEmail(),
                u.getTelefone(), u.getEndereco(), u.getLatitude(), u.getLongitude());
    }
}
