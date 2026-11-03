package br.mackenzie.resgatai.reserva;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    /** UC01-3c: a instituição tem reserva aguardando retirada? */
    boolean existsByInstituicaoIdAndStatus(Long instituicaoId, StatusReserva status);

    List<Reserva> findByInstituicaoIdOrderByDataHoraDesc(Long instituicaoId);
}
