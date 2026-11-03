package br.mackenzie.resgatai.usuario;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByDocumento(String documento);

    Optional<Usuario> findByEmailIgnoreCase(String email);

    @Query("select count(a) from Administrador a where a.ativo = true")
    long contarAdministradoresAtivos();

    /** UC03, passo 4: candidatas à notificação (a aprovação e a distância são checadas pelo Motor). */
    @Query("select i from Instituicao i where i.ativo = true")
    List<Instituicao> listarInstituicoesAtivas();
}
