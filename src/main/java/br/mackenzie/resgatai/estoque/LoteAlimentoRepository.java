package br.mackenzie.resgatai.estoque;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoteAlimentoRepository extends JpaRepository<LoteAlimento, Long> {

    /** UC01-3c: o doador tem lotes ainda ativos? */
    boolean existsByEstabelecimentoIdAndStatusIn(Long estabelecimentoId, Collection<StatusLote> status);

    /** UC02-3a: lotes já cadastrados pelo Doador, do mais recente para o mais antigo. */
    List<LoteAlimento> findByEstabelecimentoIdOrderByDataCadastroDesc(Long estabelecimentoId);

    List<LoteAlimento> findByStatus(StatusLote status);

    /**
     * UC04, passo 5: bloqueio transacional (SELECT ... FOR UPDATE) sobre o registro do lote.
     * Outras transações que tentem reservar o mesmo lote esperam até esta terminar.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LoteAlimento l where l.id = :id")
    Optional<LoteAlimento> buscarParaReserva(@Param("id") Long id);
}
