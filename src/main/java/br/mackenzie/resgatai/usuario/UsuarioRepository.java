package br.mackenzie.resgatai.usuario;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByDocumento(String documento);

    boolean existsByDocumentoOrEmail(String documento, String email);
}
