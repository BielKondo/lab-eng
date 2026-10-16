package br.mackenzie.resgatai.usuario;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC01 — Gerenciar Usuários e RF06 — Aprovar Instituição. */
@Service
@Transactional
public class UsuarioService {

    private final UsuarioRepository usuarios;

    public UsuarioService(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    /** Rejeita dados obrigatórios ausentes (6a) e CPF/CNPJ ou e-mail já cadastrado (6b). */
    public boolean criar(Usuario usuario) {
        if (usuario == null || vazio(usuario.getDocumento()) || vazio(usuario.getNome()) || vazio(usuario.getEmail())) {
            return false;
        }
        if (usuarios.existsByDocumentoOrEmail(usuario.getDocumento(), usuario.getEmail())) {
            return false;
        }
        usuarios.save(usuario);
        return true;
    }

    @Transactional(readOnly = true)
    public Optional<Usuario> buscarPorDocumento(String documento) {
        return usuarios.findByDocumento(documento);
    }

    public boolean atualizarNome(String documento, String novoNome) {
        if (vazio(novoNome)) {
            return false;
        }
        return usuarios.findByDocumento(documento).map(u -> {
            u.setNome(novoNome);
            return true;
        }).orElse(false);
    }

    /** Remover = inativar (3c): o histórico é preservado. */
    public boolean inativar(String documento) {
        return usuarios.findByDocumento(documento).map(u -> {
            u.setAtivo(false);
            return true;
        }).orElse(false);
    }

    public boolean aprovarInstituicao(String documento) {
        return usuarios.findByDocumento(documento)
                .filter(Instituicao.class::isInstance)
                .map(u -> {
                    ((Instituicao) u).aprovar();
                    return true;
                }).orElse(false);
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }
}
