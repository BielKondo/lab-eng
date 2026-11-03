package br.mackenzie.resgatai.api;

import br.mackenzie.resgatai.estoque.LoteAlimento;
import br.mackenzie.resgatai.estoque.LoteAlimentoService;
import br.mackenzie.resgatai.estoque.NovoLote;
import br.mackenzie.resgatai.estoque.StatusLote;
import br.mackenzie.resgatai.estoque.Urgencia;
import br.mackenzie.resgatai.notificacao.Notificacao;
import br.mackenzie.resgatai.notificacao.NotificacaoService;
import br.mackenzie.resgatai.reserva.Reserva;
import br.mackenzie.resgatai.reserva.StatusReserva;
import br.mackenzie.resgatai.usuario.DadosUsuario;
import br.mackenzie.resgatai.usuario.Instituicao;
import br.mackenzie.resgatai.usuario.Perfil;
import br.mackenzie.resgatai.comum.RegistroAuditoria;
import br.mackenzie.resgatai.usuario.Usuario;
import br.mackenzie.resgatai.usuario.UsuarioService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Representações enviadas ao front-end (as entidades JPA não saem da camada de negócio). */
public final class Dto {

    private Dto() {}

    public record UsuarioDto(Long id, Perfil perfil, String documento, String nome, String email, String telefone,
                             String endereco, Double latitude, Double longitude, boolean ativo, int scoreReputacao,
                             Set<Perfil.Permissao> permissoes, String statusAnalise, Double raioAtuacaoKm) {
        public static UsuarioDto de(Usuario u) {
            Instituicao i = u instanceof Instituicao x ? x : null;
            return new UsuarioDto(u.getId(), u.getPerfil(), u.getDocumento(), u.getNome(), u.getEmail(),
                    u.getTelefone(), u.getEndereco(), u.getLatitude(), u.getLongitude(), u.isAtivo(),
                    u.getScoreReputacao(), u.getPermissoes(),
                    i == null ? null : i.getStatusAnalise().name(), i == null ? null : i.getRaioAtuacaoKm());
        }
    }

    public record AuditoriaDto(Instant dataHora, String autor, String operacao, List<String> camposModificados,
                               String descricao) {
        public static AuditoriaDto de(RegistroAuditoria r) {
            return new AuditoriaDto(r.getDataHora(), r.getAutor(), r.getOperacao().name(),
                    r.getCamposModificados(), r.getDescricao());
        }
    }

    public record DetalhesUsuarioDto(UsuarioDto usuario, List<AuditoriaDto> historico) {
        public static DetalhesUsuarioDto de(UsuarioService.Detalhes d) {
            return new DetalhesUsuarioDto(UsuarioDto.de(d.usuario()),
                    d.historico().stream().map(AuditoriaDto::de).toList());
        }
    }

    public record LoteDto(Long id, String doador, String documentoDoador, String nome, String categoria,
                          int quantidade, String unidadeMedida, BigDecimal precoReduzido, boolean doacao, String foto,
                          Instant dataValidade, String condicoesConservacao, Instant inicioRetirada,
                          Instant fimRetirada, String enderecoRetirada, Double latitude, Double longitude,
                          StatusLote status, Instant dataCadastro, Urgencia urgencia, Double distanciaKm) {
        public static LoteDto de(LoteAlimento l, Double distanciaKm) {
            return new LoteDto(l.getId(), l.getEstabelecimento().getNome(), l.getEstabelecimento().getDocumento(),
                    l.getNome(), l.getCategoria(), l.getQuantidade(), l.getUnidadeMedida(), l.getPrecoReduzido(),
                    l.isDoacao(), l.getFoto(), l.getDataValidade(), l.getCondicoesConservacao(),
                    l.getInicioRetirada(), l.getFimRetirada(), l.getEnderecoRetirada(), l.getLatitude(),
                    l.getLongitude(), l.getStatus(), l.getDataCadastro(),
                    Urgencia.de(l.prazoRestante(Instant.now())), distanciaKm);
        }

        public static LoteDto de(LoteAlimentoService.Encontrado e) {
            return de(e.lote(), e.distanciaKm());
        }
    }

    public record ResumoLoteDto(LoteDto lote, Urgencia urgencia, String urgenciaDescricao, long horasRestantes,
                                NotificacaoService.Resultado notificacao) {
        public static ResumoLoteDto de(LoteAlimentoService.Resumo r) {
            return new ResumoLoteDto(LoteDto.de(r.lote(), null), r.urgencia(), r.urgencia().getDescricao(),
                    r.prazoRestante().toHours(), r.notificacao());
        }
    }

    public record NotificacaoDto(Long id, String tipo, String titulo, String mensagem, Long loteId,
                                 Double distanciaKm, Instant dataHora, String situacao, boolean lida) {
        public static NotificacaoDto de(Notificacao n) {
            return new NotificacaoDto(n.getId(), n.getTipo().name(), n.getTitulo(), n.getMensagem(),
                    n.getLote() == null ? null : n.getLote().getId(), n.getDistanciaKm(), n.getDataHora(),
                    n.getSituacao().name(), n.isLida());
        }
    }

    public record ReservaDto(Long id, LoteDto lote, String instituicao, Instant dataHora, String pin,
                             String codigoValidacao, String conteudoQrCode, Instant prazoLimiteRetirada,
                             StatusReserva status) {
        public static ReservaDto de(Reserva r) {
            return new ReservaDto(r.getId(), LoteDto.de(r.getLote(), null), r.getInstituicao().getNome(),
                    r.getDataHora(), r.getPin(), r.getCodigoValidacao(), r.conteudoQrCode(),
                    r.getPrazoLimiteRetirada(), r.getStatus());
        }
    }

    /** Corpo das respostas de erro. */
    public record ErroDto(String erro, Object detalhes) {}

    // ---------- entradas (um único construtor, para a desserialização do JSON ser inequívoca) ----------

    /** Formulário do UC01 (passo 4). */
    public record UsuarioForm(Perfil perfil, String documento, String nome, String email, String telefone,
                              String endereco, Double latitude, Double longitude) {
        public DadosUsuario paraDominio() {
            return new DadosUsuario(perfil, documento, nome, email, telefone, endereco, latitude, longitude);
        }
        public static UsuarioForm de(DadosUsuario d) {
            return new UsuarioForm(d.perfil(), d.documento(), d.nome(), d.email(), d.telefone(), d.endereco(),
                    d.latitude(), d.longitude());
        }
    }

    /** Formulário do UC02 (passo 2). */
    public record LoteForm(String nome, String categoria, int quantidade, String unidadeMedida, BigDecimal precoReduzido,
                           String foto, Instant dataValidade, String condicoesConservacao, Instant inicioRetirada,
                           Instant fimRetirada, String enderecoRetirada, Double latitude, Double longitude) {
        public NovoLote paraDominio() {
            return new NovoLote(nome, categoria, quantidade, unidadeMedida, precoReduzido, foto, dataValidade,
                    condicoesConservacao, inicioRetirada, fimRetirada, enderecoRetirada, latitude, longitude);
        }
        public static LoteForm de(NovoLote n) {
            return new LoteForm(n.nome(), n.categoria(), n.quantidade(), n.unidadeMedida(), n.precoReduzido(), n.foto(),
                    n.dataValidade(), n.condicoesConservacao(), n.inicioRetirada(), n.fimRetirada(),
                    n.enderecoRetirada(), n.latitude(), n.longitude());
        }
    }
}
