package br.mackenzie.resgatai.notificacao;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    List<Notificacao> findByDestinatarioIdOrderByDataHoraDesc(Long destinatarioId);

    /** Notificações de um lote, da instituição mais próxima para a mais distante (UC03, passo 5). */
    List<Notificacao> findByLoteIdAndTipoOrderByDistanciaKmAsc(Long loteId, Notificacao.Tipo tipo);
}
