package br.mackenzie.resgatai.comum;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistroAuditoriaRepository extends JpaRepository<RegistroAuditoria, Long> {

    List<RegistroAuditoria> findByUsuarioAlvoIdOrderByIdAsc(Long usuarioAlvoId);
}
