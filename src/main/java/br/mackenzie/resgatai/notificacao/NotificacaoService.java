package br.mackenzie.resgatai.notificacao;

import br.mackenzie.resgatai.comum.Notificador;
import br.mackenzie.resgatai.comum.NaoEncontradoException;
import br.mackenzie.resgatai.estoque.LoteAlimento;
import br.mackenzie.resgatai.estoque.StatusLote;
import br.mackenzie.resgatai.usuario.Instituicao;
import br.mackenzie.resgatai.usuario.Usuario;
import br.mackenzie.resgatai.usuario.UsuarioRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC03 — Notificar Alimentos Próximos (incluído pelo UC02, passo 11) e as notificações
 * pontuais do UC04 (reserva recebida — passo 11; lote expirado — 6b).
 *
 * <p>Cada envio é registrado em {@link Notificacao}, que é o que o Receptor consulta no
 * passo 1 do UC04. Falhas do canal de envio não desfazem o lote nem a reserva (UC03-6a).</p>
 */
@Service
@Transactional
public class NotificacaoService {

    public static final String SEM_LOCALIZACAO = "Lote sem coordenadas de retirada: distância não calculável.";
    public static final String SEM_ELEGIVEIS = "Nenhum receptor elegível no raio de alcance.";
    public static final String LOTE_INDISPONIVEL = "Lote não está disponível para notificação.";

    /**
     * Resumo devolvido ao UC02 (passo 8): raio usado, se foi ampliado (4a), quantos receptores foram
     * notificados, quantos envios falharam (6a) e, quando nada foi enviado, o motivo (2a, 3a, 4a).
     */
    public record Resultado(double raioKm, boolean raioAmpliado, int notificados, int falhas, String motivoSemEnvio) {
        static Resultado semEnvio(String motivo) {
            return new Resultado(0, false, 0, 0, motivo);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(NotificacaoService.class);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.forLanguageTag("pt-BR"))
            .withZone(ZoneId.of("America/Sao_Paulo"));

    private final NotificacaoRepository notificacoes;
    private final UsuarioRepository usuarios;
    private final Notificador canal;

    public NotificacaoService(NotificacaoRepository notificacoes, UsuarioRepository usuarios, Notificador canal) {
        this.notificacoes = notificacoes;
        this.usuarios = usuarios;
        this.canal = canal;
    }

    // ------------------------------------------------------------------
    // UC03 — fluxo principal
    // ------------------------------------------------------------------

    /**
     * Passos 1-8: calcula o tempo restante e o raio, seleciona os Receptores elegíveis,
     * envia e registra cada notificação e devolve o resumo ao UC02.
     */
    public Resultado notificarAlimentosProximos(LoteAlimento lote) {
        Instant agora = Instant.now();
        if (lote.getStatus() != StatusLote.DISPONIVEL || lote.vencido(agora)) {   // exceção 2a
            return Resultado.semEnvio(LOTE_INDISPONIVEL);
        }
        if (!lote.possuiLocalizacao()) {                                          // alternativo 3a
            return Resultado.semEnvio(SEM_LOCALIZACAO);
        }

        Duration restante = lote.prazoRestante(agora);                            // passo 2
        double raio = MotorRoteamento.raioKm(restante);                           // passo 3
        List<Instituicao> candidatas = usuarios.listarInstituicoesAtivas();
        List<MotorRoteamento.Elegivel> elegiveis = MotorRoteamento.elegiveis(lote, candidatas, raio); // passos 4-5

        boolean ampliado = false;
        if (elegiveis.isEmpty() && raio < MotorRoteamento.RAIO_MAXIMO_KM) {     // alternativo 4a
            raio = MotorRoteamento.ampliar(raio);
            ampliado = true;
            elegiveis = MotorRoteamento.elegiveis(lote, candidatas, raio);
        }
        if (elegiveis.isEmpty()) {
            return new Resultado(raio, ampliado, 0, 0, SEM_ELEGIVEIS);
        }

        int enviadas = 0;
        int falhas = 0;
        for (MotorRoteamento.Elegivel e : elegiveis) {                                    // passos 6-7
            Notificacao n = new Notificacao(e.instituicao(), Notificacao.Tipo.NOVO_LOTE_PROXIMO, lote,
                    "Lote disponível a " + formatarKm(e.distanciaKm()) + " de você",
                    mensagemNovoLote(lote, e.distanciaKm(), restante), e.distanciaKm());
            if (enviar(n)) enviadas++; else falhas++;
        }
        return new Resultado(raio, ampliado, enviadas, falhas, null);  // passo 8
    }

    // ------------------------------------------------------------------
    // Notificações do UC04
    // ------------------------------------------------------------------

    /** UC04, passo 11: avisa o Doador de que o lote foi reservado. */
    public Notificacao notificarReservaAoDoador(LoteAlimento lote, Instituicao receptor, Instant prazoLimite) {
        Notificacao n = new Notificacao(lote.getEstabelecimento(), Notificacao.Tipo.RESERVA_RECEBIDA, lote,
                "Lote reservado: " + lote.getNome(),
                receptor.getNome() + " reservou " + lote.getQuantidade() + " " + lote.getUnidadeMedida()
                        + " de " + lote.getNome() + ". Retirada até " + HORA.format(prazoLimite)
                        + " em " + lote.getEnderecoRetirada() + ".", null);
        enviar(n);
        return n;
    }

    /** UC04, 6b: avisa o Doador de que o lote venceu ou saiu da janela de retirada sem ser reservado. */
    public Notificacao notificarLoteExpirado(LoteAlimento lote) {
        Notificacao n = new Notificacao(lote.getEstabelecimento(), Notificacao.Tipo.LOTE_EXPIRADO, lote,
                "Lote expirado: " + lote.getNome(),
                "O lote " + lote.getNome() + " venceu ou saiu da janela de retirada e foi retirado das listagens.", null);
        enviar(n);
        return n;
    }

    // ------------------------------------------------------------------
    // Consulta (UC04, passo 1)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Notificacao> listar(Usuario destinatario) {
        return notificacoes.findByDestinatarioIdOrderByDataHoraDesc(destinatario.getId());
    }

    public void marcarComoLida(Usuario destinatario, Long notificacaoId) {
        Notificacao n = notificacoes.findById(notificacaoId)
                .filter(x -> x.getDestinatario().getId().equals(destinatario.getId()))
                .orElseThrow(() -> new NaoEncontradoException("Notificação não encontrada: " + notificacaoId));
        n.marcarComoLida();
    }

    // ------------------------------------------------------------------

    /** Envia pelo canal e registra; uma falha do canal vira registro FALHA, sem interromper (UC03-6a). */
    private boolean enviar(Notificacao n) {
        try {
            canal.enviar(n.getDestinatario().getEmail(), n.getTitulo(), n.getMensagem());
        } catch (RuntimeException falha) {
            log.warn("Falha ao notificar {}: {}", n.getDestinatario().getEmail(), falha.getMessage());
            n.registrarFalha(falha.getMessage() == null ? falha.getClass().getSimpleName() : falha.getMessage());
        }
        notificacoes.save(n);
        return n.getSituacao() == Notificacao.Situacao.ENVIADA;
    }

    private static String mensagemNovoLote(LoteAlimento lote, double distanciaKm, Duration restante) {
        return lote.getEstabelecimento().getNome() + " publicou " + lote.getQuantidade() + " "
                + lote.getUnidadeMedida() + " de " + lote.getNome()
                + (lote.isDoacao() ? " (doação)" : " por R$ " + lote.getPrecoReduzido())
                + ", a " + formatarKm(distanciaKm) + ". Vence em " + formatarDuracao(restante)
                + ". Retirada entre " + HORA.format(lote.getInicioRetirada()) + " e "
                + HORA.format(lote.getFimRetirada()) + " em " + lote.getEnderecoRetirada() + ".";
    }

    static String formatarKm(double km) {
        return km < 1 ? Math.round(km * 1000) + " m" : String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", km);
    }

    static String formatarDuracao(Duration d) {
        long horas = d.toHours();
        long minutos = d.toMinutesPart();
        return horas > 0 ? horas + "h" + (minutos > 0 ? minutos + "min" : "") : minutos + "min";
    }
}
