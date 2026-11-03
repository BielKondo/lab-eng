package br.mackenzie.resgatai.estoque;

import br.mackenzie.resgatai.comum.AcessoNegadoException;
import br.mackenzie.resgatai.comum.DadosInvalidosException;
import br.mackenzie.resgatai.comum.NaoEncontradoException;
import br.mackenzie.resgatai.notificacao.MotorRoteamento;
import br.mackenzie.resgatai.notificacao.NotificacaoService;
import br.mackenzie.resgatai.usuario.Estabelecimento;
import br.mackenzie.resgatai.usuario.Instituicao;
import br.mackenzie.resgatai.comum.RegistroAuditoria;
import br.mackenzie.resgatai.comum.RegistroAuditoriaRepository;
import br.mackenzie.resgatai.usuario.UsuarioService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC02 — Cadastrar Lote de Alimentos, e a busca de lotes disponíveis (RF03 / UC04-2a).
 *
 * <ul>
 *   <li>Passo 6 (indicador de urgência): {@link #indicadorUrgencia}</li>
 *   <li>Passos 8-12 (confirmar, validar, publicar, incluir UC03, resumo): {@link #cadastrar}</li>
 *   <li>3a (repetir lote anterior): {@link #listarDoDoador} e {@link #prepararRepeticao}</li>
 *   <li>7a (endereço de retirada diferente): campo {@code enderecoRetirada} de {@link NovoLote}</li>
 *   <li>5a, 7a, 9a (validade, janela, obrigatórios): {@link DadosInvalidosException}</li>
 *   <li>UC04-2a (busca por critérios): {@link #buscarDisponiveis}</li>
 * </ul>
 *
 * <p>Pré-condição: Doador identificado, ativo e com endereço de retirada cadastrado.</p>
 */
@Service
@Transactional
public class LoteAlimentoService {

    public static final String SO_DOADOR = "Apenas doadores ativos podem cadastrar lotes.";
    public static final String SEM_ENDERECO = "O doador precisa ter um endereço de retirada cadastrado.";
    public static final String SO_RECEPTOR = "Apenas receptores ativos podem buscar lotes.";
    public static final String OBRIGATORIO = "Campo obrigatório não preenchido.";
    public static final String VENCIDO = "Data de validade já passou: lotes vencidos não são publicados.";
    public static final String JANELA_INVERTIDA = "O início da janela de retirada deve ser antes do fim.";
    public static final String RETIRADA_APOS_VENCIMENTO = "A janela de retirada termina depois do vencimento do lote.";
    public static final String JANELA_ENCERRADA = "A janela de retirada já se encerrou.";

    /** UC02, passo 12: resumo do lote publicado, com o indicador de urgência e o resultado do UC03. */
    public record Resumo(LoteAlimento lote, Urgencia urgencia, Duration prazoRestante, NotificacaoService.Resultado notificacao) {}

    /** Item da busca (UC04-2a): o lote e a distância até o Receptor, quando calculável. */
    public record Encontrado(LoteAlimento lote, Double distanciaKm) {}

    private final LoteAlimentoRepository lotes;
    private final UsuarioService usuarios;
    private final RegistroAuditoriaRepository auditoria;
    private final NotificacaoService notificacoes;

    public LoteAlimentoService(LoteAlimentoRepository lotes, UsuarioService usuarios,
                               RegistroAuditoriaRepository auditoria, NotificacaoService notificacoes) {
        this.lotes = lotes;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.notificacoes = notificacoes;
    }

    // ------------------------------------------------------------------
    // Fluxo principal
    // ------------------------------------------------------------------

    /** Passo 6: prazo restante até o vencimento e o indicador de urgência correspondente. */
    public Urgencia indicadorUrgencia(Instant dataValidade) {
        if (dataValidade == null) {
            throw new DadosInvalidosException(Map.of("dataValidade", OBRIGATORIO));
        }
        return Urgencia.de(Duration.between(Instant.now(), dataValidade));
    }

    /**
     * Passos 8-12: valida os dados (5a, 7a, 9a), gera o identificador, atribui DISPONIVEL,
     * associa o endereço de retirada (o do Doador ou o alternativo — 7a), persiste,
     * inclui o UC03 e devolve o resumo do lote publicado.
     */
    public Resumo cadastrar(String documentoDoador, NovoLote dados) {
        Estabelecimento doador = exigirDoador(documentoDoador);
        Instant agora = Instant.now();
        validar(dados, agora);                                                   // passo 9

        boolean enderecoAlternativo = dados.enderecoRetirada() != null && !dados.enderecoRetirada().isBlank();
        String endereco = enderecoAlternativo ? dados.enderecoRetirada().trim() : doador.getEndereco();
        Double latitude = enderecoAlternativo ? dados.latitude() : doador.getLatitude();
        Double longitude = enderecoAlternativo ? dados.longitude() : doador.getLongitude();

        LoteAlimento lote = lotes.save(new LoteAlimento(doador, dados, endereco, latitude, longitude)); // passo 10
        auditoria.save(new RegistroAuditoria(doador.getDocumento(), RegistroAuditoria.Operacao.CADASTRO_LOTE,
                doador.getId(), List.of(), "Lote " + lote.getId() + " (" + lote.getNome() + ") publicado"
                + (enderecoAlternativo ? " com endereço de retirada alternativo." : ".")));

        NotificacaoService.Resultado resultado = notificacoes.notificarAlimentosProximos(lote); // passo 11 (UC03)
        Duration restante = lote.prazoRestante(agora);
        return new Resumo(lote, Urgencia.de(restante), restante, resultado);      // passo 12
    }

    // ------------------------------------------------------------------
    // 3a — cadastro a partir de lote anterior
    // ------------------------------------------------------------------

    /** 3a, passo 2: lotes já cadastrados pelo Doador (todos os status, do mais recente ao mais antigo). */
    @Transactional(readOnly = true)
    public List<LoteAlimento> listarDoDoador(String documentoDoador) {
        Estabelecimento doador = exigirDoador(documentoDoador);
        return lotes.findByEstabelecimentoIdOrderByDataCadastroDesc(doador.getId());
    }

    /**
     * 3a, passo 4: formulário preenchido com os dados do lote de referência, exceto quantidade
     * e data de validade (devolvidas zeradas para o Doador informar). A janela de retirada anterior
     * só é reaproveitada se ainda não tiver passado, e o endereço alternativo do lote anterior é
     * mantido apenas se for diferente do endereço atual do Doador.
     */
    @Transactional(readOnly = true)
    public NovoLote prepararRepeticao(String documentoDoador, Long loteId) {
        Estabelecimento doador = exigirDoador(documentoDoador);
        LoteAlimento ref = lotes.findById(loteId)
                .filter(l -> l.getEstabelecimento().getId().equals(doador.getId()))
                .orElseThrow(() -> new NaoEncontradoException("Lote não encontrado entre os seus cadastros: " + loteId));
        boolean enderecoAlternativo = !ref.getEnderecoRetirada().equals(doador.getEndereco());
        boolean janelaAindaValida = ref.getFimRetirada().isAfter(Instant.now());
        return new NovoLote(ref.getNome(), ref.getCategoria(), 0, ref.getUnidadeMedida(), ref.getPrecoReduzido(),
                ref.getFoto(), null, ref.getCondicoesConservacao(),
                janelaAindaValida ? ref.getInicioRetirada() : null, janelaAindaValida ? ref.getFimRetirada() : null,
                enderecoAlternativo ? ref.getEnderecoRetirada() : null,
                enderecoAlternativo ? ref.getLatitude() : null,
                enderecoAlternativo ? ref.getLongitude() : null);
    }

    // ------------------------------------------------------------------
    // Consulta pelos Receptores (UC04, passos 2-3 e 2a)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public LoteAlimento detalhar(Long loteId) {
        return lotes.findById(loteId)
                .orElseThrow(() -> new NaoEncontradoException("Lote não encontrado: " + loteId));
    }

    /**
     * UC04-2a / RF03: lotes DISPONIVEL, não vencidos e com janela aberta, filtrados por categoria,
     * preço máximo, prazo de vencimento e raio (km) a partir da localização do Receptor.
     * Critérios nulos são ignorados; sem raio informado, vale o raio de atuação do Receptor, se houver.
     * Ordena pela distância (lotes sem coordenadas vão para o fim, ordenados pelo vencimento).
     */
    @Transactional(readOnly = true)
    public List<Encontrado> buscarDisponiveis(String documentoReceptor, String categoria, BigDecimal precoMaximo,
                                              Instant validadeAte, Double raioKm) {
        Instituicao receptor = exigirReceptor(documentoReceptor);
        Double raio = raioKm != null ? raioKm : receptor.getRaioAtuacaoKm();
        Instant agora = Instant.now();

        return lotes.findByStatus(StatusLote.DISPONIVEL).stream()
                .filter(l -> l.aptoParaReserva(agora))
                .filter(l -> vazio(categoria) || categoria.trim().equalsIgnoreCase(l.getCategoria()))
                .filter(l -> precoMaximo == null || l.isDoacao() || l.getPrecoReduzido().compareTo(precoMaximo) <= 0)
                .filter(l -> validadeAte == null || !l.getDataValidade().isAfter(validadeAte))
                .map(l -> new Encontrado(l, MotorRoteamento.distanciaKm(l, receptor).orElse(null)))
                .filter(e -> raio == null || (e.distanciaKm() != null && e.distanciaKm() <= raio))
                .sorted(Comparator.comparing((Encontrado e) -> e.distanciaKm() == null ? Double.MAX_VALUE : e.distanciaKm())
                        .thenComparing(e -> e.lote().getDataValidade()))
                .toList();
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    /** Pré-condição do UC02: Doador ativo com endereço de retirada validado. */
    private Estabelecimento exigirDoador(String documento) {
        Estabelecimento doador = usuarios.buscarPorDocumento(documento)
                .filter(u -> u instanceof Estabelecimento && u.isAtivo())
                .map(Estabelecimento.class::cast)
                .orElseThrow(() -> new AcessoNegadoException(SO_DOADOR));
        if (doador.getEndereco() == null || doador.getEndereco().isBlank()) {
            throw new IllegalStateException(SEM_ENDERECO);
        }
        return doador;
    }

    private Instituicao exigirReceptor(String documento) {
        return usuarios.buscarPorDocumento(documento)
                .filter(u -> u instanceof Instituicao && u.isAtivo())
                .map(Instituicao.class::cast)
                .orElseThrow(() -> new AcessoNegadoException(SO_RECEPTOR));
    }

    /** Exceções 9a (obrigatórios), 5a (validade) e 7a (janela) — todas apontadas de uma vez. */
    private static void validar(NovoLote d, Instant agora) {
        Map<String, String> erros = new LinkedHashMap<>();
        if (d == null) {
            throw new DadosInvalidosException(Map.of("formulario", "Nenhum dado foi informado."));
        }
        if (vazio(d.nome())) erros.put("nome", OBRIGATORIO);
        else if (d.nome().trim().length() > 150) erros.put("nome", "No máximo 150 caracteres.");
        if (d.categoria() != null && d.categoria().trim().length() > 60) erros.put("categoria", "No máximo 60 caracteres.");
        if (d.quantidade() <= 0) erros.put("quantidade", "A quantidade deve ser maior que zero.");
        if (vazio(d.unidadeMedida())) erros.put("unidadeMedida", OBRIGATORIO);
        else if (d.unidadeMedida().trim().length() > 20) erros.put("unidadeMedida", "No máximo 20 caracteres.");
        if (d.foto() != null && d.foto().length() > 500) erros.put("foto", "URL da foto com mais de 500 caracteres.");
        if (d.condicoesConservacao() != null && d.condicoesConservacao().length() > 500) {
            erros.put("condicoesConservacao", "No máximo 500 caracteres.");
        }
        if (d.precoReduzido() != null && d.precoReduzido().signum() < 0) {
            erros.put("precoReduzido", "O preço reduzido não pode ser negativo.");
        }
        if (d.dataValidade() == null) {
            erros.put("dataValidade", OBRIGATORIO);
        } else if (!d.dataValidade().isAfter(agora)) {
            erros.put("dataValidade", VENCIDO);                                   // 5a
        }
        if (d.inicioRetirada() == null) erros.put("inicioRetirada", OBRIGATORIO);
        if (d.fimRetirada() == null) erros.put("fimRetirada", OBRIGATORIO);
        if (d.inicioRetirada() != null && d.fimRetirada() != null) {
            if (!d.inicioRetirada().isBefore(d.fimRetirada())) {
                erros.put("fimRetirada", JANELA_INVERTIDA);
            } else if (!d.fimRetirada().isAfter(agora)) {
                erros.put("fimRetirada", JANELA_ENCERRADA);                       // ninguém conseguiria retirar
            } else if (d.dataValidade() != null && d.fimRetirada().isAfter(d.dataValidade())) {
                erros.put("fimRetirada", RETIRADA_APOS_VENCIMENTO);               // 7a
            }
        }
        if (d.enderecoRetirada() != null && !d.enderecoRetirada().isBlank()) {
            if (d.enderecoRetirada().trim().length() < 5) erros.put("enderecoRetirada", "Endereço de retirada incompleto.");
            else if (d.enderecoRetirada().trim().length() > 255) erros.put("enderecoRetirada", "No máximo 255 caracteres.");
        }
        if ((d.latitude() == null) != (d.longitude() == null)) {
            erros.put("localizacao", "Informe latitude e longitude juntas.");
        } else if (d.latitude() != null && (Math.abs(d.latitude()) > 90 || Math.abs(d.longitude()) > 180)) {
            erros.put("localizacao", "Coordenadas fora da faixa válida.");
        }
        if (!erros.isEmpty()) {
            throw new DadosInvalidosException(erros);
        }
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }
}
