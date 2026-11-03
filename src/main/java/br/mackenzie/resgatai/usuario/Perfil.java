package br.mackenzie.resgatai.usuario;

import java.util.Set;

/**
 * Tipo de perfil escolhido no cadastro (UC01, passo 4) e as permissões que ele concede
 * nas tabelas de segurança (UC01, passo 7).
 */
public enum Perfil {

    DOADOR(Set.of(Permissao.CADASTRAR_LOTE, Permissao.VALIDAR_RETIRADA)),
    RECEPTOR(Set.of(Permissao.BUSCAR_OFERTAS, Permissao.RESERVAR_LOTE)),
    ADMINISTRADOR(Set.of(Permissao.GERENCIAR_USUARIOS, Permissao.APROVAR_INSTITUICAO));

    /** Permissões de acesso (os nomes seguem os métodos do diagrama de classes). */
    public enum Permissao {
        CADASTRAR_LOTE, VALIDAR_RETIRADA, BUSCAR_OFERTAS, RESERVAR_LOTE, GERENCIAR_USUARIOS, APROVAR_INSTITUICAO
    }

    private final Set<Permissao> permissoes;

    Perfil(Set<Permissao> permissoes) {
        this.permissoes = permissoes;
    }

    public Set<Permissao> getPermissoes() {
        return permissoes;
    }
}
