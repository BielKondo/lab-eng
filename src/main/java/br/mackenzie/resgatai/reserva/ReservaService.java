package br.mackenzie.resgatai.reserva;

import br.mackenzie.resgatai.comum.NaoEncontradoException;

import br.mackenzie.resgatai.comum.AcessoNegadoException;

import br.mackenzie.resgatai.estoque.LoteAlimento;
import br.mackenzie.resgatai.estoque.LoteAlimentoRepository;
import br.mackenzie.resgatai.estoque.LoteAlimentoService;
import br.mackenzie.resgatai.estoque.StatusLote;
import br.mackenzie.resgatai.notificacao.NotificacaoService;
import br.mackenzie.resgatai.usuario.Instituicao;
import br.mackenzie.resgatai.comum.RegistroAuditoria;
import br.mackenzie.resgatai.comum.RegistroAuditoriaRepository;
import br.mackenzie.resgatai.usuario.UsuarioService;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC04 — Reservar Lote de Alimentos.
 *
 * <ul>
 *   <li>Passo 1 (notificação): {@link NotificacaoService#listar}</li>
 *   <li>Passos 2-3 e 2a (detalhes / busca): {@link LoteAlimentoService#detalhar} e {@link LoteAlimentoService#buscarDisponiveis}</li>
 *   <li>Passos 4-11 (bloqueio, verificação, reserva, PIN/QR, prazo, notificação): {@link #reservar}</li>
 *   <li>4a (desistir antes de confirmar): nada é chamado — sem bloqueio e sem alteração</li>
 *   <li>6a: {@link LoteIndisponivelException}; 6b: {@link LoteExpiradoException}</li>
 * </ul>
 *
 * <p>Pré-condição: Receptor identificado, ativo e com cadastro aprovado pelo Administrador (RF06).</p>
 */
@Service
@Transactional
public class ReservaService {

    public static final String SO_RECEPTOR = "Apenas receptores ativos e aprovados podem reservar lotes.";

    private final LoteAlimentoRepository lotes;
    private final LoteAlimentoService loteService;
    private final ReservaRepository reservas;
    private final UsuarioService usuarios;
    private final NotificacaoService notificacoes;
    private final RegistroAuditoriaRepository auditoria;
    private final SecureRandom aleatorio = new SecureRandom();

    public ReservaService(LoteAlimentoRepository lotes, LoteAlimentoService loteService, ReservaRepository reservas,
                          UsuarioService usuarios, NotificacaoService notificacoes, RegistroAuditoriaRepository auditoria) {
        this.lotes = lotes;
        this.loteService = loteService;
        this.reservas = reservas;
        this.usuarios = usuarios;
        this.notificacoes = notificacoes;
        this.auditoria = auditoria;
    }

    /**
     * Passos 4-11. O bloqueio (passo 5) é o {@code SELECT ... FOR UPDATE} de
     * {@link LoteAlimentoRepository#buscarParaReserva}; ele é liberado quando a transação termina
     * (passo 9) — tanto no commit quanto no rollback das exceções 6a/6b.
     *
     * @throws LoteIndisponivelException 6a — lote já reservado por outro Receptor (transação desfeita)
     * @throws LoteExpiradoException     6b — lote vencido ou fora da janela; o status EXPIRADO é gravado
     */
    @Transactional(noRollbackFor = LoteExpiradoException.class)
    public Reserva reservar(String documentoReceptor, Long loteId) {
        Instituicao receptor = exigirReceptor(documentoReceptor);
        LoteAlimento lote = lotes.buscarParaReserva(loteId)                                // passo 5 (lock)
                .orElseThrow(() -> new NaoEncontradoException("Lote não encontrado: " + loteId));
        Instant agora = Instant.now();

        if (lote.getStatus() != StatusLote.DISPONIVEL) {                                   // passo 6 → 6a
            List<LoteAlimentoService.Encontrado> alternativas = loteService.buscarDisponiveis(documentoReceptor, null, null, null, null)
                    .stream().filter(e -> !e.lote().getId().equals(loteId)).toList();
            throw new LoteIndisponivelException(lote, alternativas);
        }
        if (lote.vencido(agora) || lote.janelaEncerrada(agora)) {                          // 6b
            lote.alterarStatus(StatusLote.EXPIRADO);
            auditoria.save(new RegistroAuditoria(receptor.getDocumento(), RegistroAuditoria.Operacao.LOTE_EXPIRADO,
                    lote.getEstabelecimento().getId(), List.of(), "Lote " + lote.getId() + " expirado ao tentar reservar."));
            notificacoes.notificarLoteExpirado(lote);
            throw new LoteExpiradoException(lote);
        }

        lote.alterarStatus(StatusLote.RESERVADO_PENDENTE);                                 // passo 7
        Reserva reserva = new Reserva(lote, receptor, lote.getFimRetirada());              // passo 8: prazo = janela do Doador
        reserva.gerarCodigo(aleatorio);
        reservas.save(reserva);
        auditoria.save(new RegistroAuditoria(receptor.getDocumento(), RegistroAuditoria.Operacao.RESERVA_LOTE,
                receptor.getId(), List.of(), "Reserva " + reserva.getId() + " do lote " + lote.getId() + "."));

        notificacoes.notificarReservaAoDoador(lote, receptor, reserva.getPrazoLimiteRetirada()); // passo 11
        return reserva; // commit libera o bloqueio (passo 9); o lote some das buscas por não estar DISPONIVEL (passo 10)
    }

    @Transactional(readOnly = true)
    public List<Reserva> minhasReservas(String documentoReceptor) {
        Instituicao receptor = exigirReceptor(documentoReceptor);
        return reservas.findByInstituicaoIdOrderByDataHoraDesc(receptor.getId());
    }

    @Transactional(readOnly = true)
    public Reserva detalhar(String documentoReceptor, Long reservaId) {
        Instituicao receptor = exigirReceptor(documentoReceptor);
        return reservas.findById(reservaId)
                .filter(r -> r.getInstituicao().getId().equals(receptor.getId()))
                .orElseThrow(() -> new NaoEncontradoException("Reserva não encontrada: " + reservaId));
    }

    private Instituicao exigirReceptor(String documento) {
        return usuarios.buscarPorDocumento(documento)
                .filter(u -> u instanceof Instituicao i && i.isAtivo() && i.isAprovada())
                .map(Instituicao.class::cast)
                .orElseThrow(() -> new AcessoNegadoException(SO_RECEPTOR));
    }
}
