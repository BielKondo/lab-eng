package br.mackenzie.resgatai.usuario;

import br.mackenzie.resgatai.comum.AcessoNegadoException;
import br.mackenzie.resgatai.comum.DadosInvalidosException;
import br.mackenzie.resgatai.comum.NaoEncontradoException;
import br.mackenzie.resgatai.comum.Notificador;
import br.mackenzie.resgatai.comum.RegistroAuditoria;
import br.mackenzie.resgatai.comum.RegistroAuditoriaRepository;
import br.mackenzie.resgatai.estoque.LoteAlimentoRepository;
import br.mackenzie.resgatai.estoque.StatusLote;
import br.mackenzie.resgatai.reserva.ReservaRepository;
import br.mackenzie.resgatai.reserva.StatusReserva;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC01 — Gerenciar Usuários (CRUD) e RF06 — Aprovar Instituição.
 *
 * <p>Pré-condição de toda operação do UC01: o documento informado como operador pertence a um
 * Administrador ativo com a permissão {@link Perfil.Permissao#GERENCIAR_USUARIOS}.</p>
 *
 * <ul>
 *   <li>Fluxo principal, passos 1-2 (listar): {@link #listar}</li>
 *   <li>Fluxo principal, passos 3-9 (criar): {@link #cadastrar}</li>
 *   <li>3a consultar: {@link #consultar} e {@link #detalhar}</li>
 *   <li>3b atualizar: {@link #prepararAtualizacao} e {@link #atualizar}</li>
 *   <li>3c remover (inativar): {@link #prepararInativacao} e {@link #inativar}</li>
 *   <li>6a: {@link DadosInvalidosException}; 6b: {@link UsuarioJaCadastradoException}</li>
 * </ul>
 */
@Service
@Transactional
public class UsuarioService {

    public static final String SO_ADMINISTRADOR = "Apenas administradores ativos podem gerenciar usuários.";
    public static final String URL_PRIMEIRO_ACESSO = "https://resgatai.app/primeiro-acesso?token=";

    /** UC01, 3a, passo 4: dados cadastrais, perfil de acesso, score de reputação e histórico de operações. */
    public record Detalhes(Usuario usuario, Perfil perfil, Set<Perfil.Permissao> permissoes,
                           int scoreReputacao, List<RegistroAuditoria> historico) {}

    private final UsuarioRepository usuarios;
    private final RegistroAuditoriaRepository auditoria;
    private final Notificador servicoEmail;
    private final BaseValidacaoInstituicoes baseValidacao;
    private final LoteAlimentoRepository lotes;
    private final ReservaRepository reservas;

    public UsuarioService(UsuarioRepository usuarios, RegistroAuditoriaRepository auditoria,
                          Notificador servicoEmail, BaseValidacaoInstituicoes baseValidacao,
                          LoteAlimentoRepository lotes, ReservaRepository reservas) {
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.servicoEmail = servicoEmail;
        this.baseValidacao = baseValidacao;
        this.lotes = lotes;
        this.reservas = reservas;
    }

    /**
     * Cria o primeiro Administrador. Só é permitido enquanto não houver administrador ativo;
     * sem ele ninguém atenderia a pré-condição do UC01.
     */
    public Administrador criarAdministradorInicial(DadosUsuario dados) {
        if (usuarios.contarAdministradoresAtivos() > 0) {
            throw new IllegalStateException("O sistema já possui um administrador ativo.");
        }
        if (dados != null && dados.perfil() != Perfil.ADMINISTRADOR) {
            throw new IllegalArgumentException("O usuário inicial deve ter perfil Administrador.");
        }
        validarEVerificarDuplicidade(dados, null);
        Administrador admin = (Administrador) novaEntidade(dados);
        usuarios.save(admin);
        auditar(admin, RegistroAuditoria.Operacao.CADASTRO_ADMINISTRADOR_INICIAL, admin, List.of(),
                "Administrador inicial do sistema.");
        return admin;
    }

    // ---------- Fluxo principal ----------

    /** Passo 2: lista de usuários cadastrados (ativos e inativos). */
    @Transactional(readOnly = true)
    public List<Usuario> listar(String documentoAdministrador) {
        exigirAdministrador(documentoAdministrador);
        return todosOrdenados();
    }

    /**
     * Passos 5-9: valida (6a), verifica duplicidade (6b), grava, vincula perfil e permissões,
     * inicializa o score, envia o e-mail de primeiro acesso e registra a auditoria.
     * Se o e-mail falhar, a exceção desfaz a transação inteira.
     */
    public Usuario cadastrar(String documentoAdministrador, DadosUsuario dados) {
        Administrador admin = exigirAdministrador(documentoAdministrador);
        validarEVerificarDuplicidade(dados, null);                          // passo 6

        Usuario novo = novaEntidade(dados);                                  // passo 7
        String token = UUID.randomUUID().toString().replace("-", "");
        novo.setTokenPrimeiroAcesso(token);
        usuarios.save(novo);

        try {                                                                // passo 8
            servicoEmail.enviar(novo.getEmail(), "ResgatAI - defina sua senha",
                    "Olá, " + novo.getNome() + "! Seu cadastro como " + novo.getPerfil()
                            + " foi criado. Defina sua senha em: " + URL_PRIMEIRO_ACESSO + token);
        } catch (RuntimeException falha) {
            throw new IllegalStateException("Não foi possível enviar o e-mail de primeiro acesso; "
                    + "o cadastro não foi realizado.", falha);
        }

        auditar(admin, RegistroAuditoria.Operacao.CADASTRO, novo, List.of(),   // passo 9
                "Usuário cadastrado com perfil " + novo.getPerfil() + ".");
        return novo;
    }

    // ---------- 3a: consultar ----------

    /** Filtra por parte do nome, CNPJ/CPF, perfil e situação (ativo). Critérios nulos são ignorados. */
    @Transactional(readOnly = true)
    public List<Usuario> consultar(String documentoAdministrador, String nome, String documento,
                                   Perfil perfil, Boolean ativo) {
        exigirAdministrador(documentoAdministrador);
        return todosOrdenados().stream()
                .filter(u -> vazio(nome) || semAcento(u.getNome()).contains(semAcento(nome)))
                .filter(u -> vazio(documento) || ValidadorUsuario.somenteDigitos(u.getDocumento())
                        .equals(ValidadorUsuario.somenteDigitos(documento)))
                .filter(u -> perfil == null || u.getPerfil() == perfil)
                .filter(u -> ativo == null || u.isAtivo() == ativo)
                .toList();
    }

    @Transactional(readOnly = true)
    public Detalhes detalhar(String documentoAdministrador, String documento) {
        exigirAdministrador(documentoAdministrador);
        Usuario u = buscarExistente(documento);
        return new Detalhes(u, u.getPerfil(), u.getPermissoes(), u.getScoreReputacao(),
                auditoria.findByUsuarioAlvoIdOrderByIdAsc(u.getId()));
    }

    // ---------- 3b: atualizar ----------

    /** Passo 2: formulário preenchido com os dados atuais. */
    @Transactional(readOnly = true)
    public DadosUsuario prepararAtualizacao(String documentoAdministrador, String documento) {
        exigirAdministrador(documentoAdministrador);
        return DadosUsuario.de(buscarExistente(documento));
    }

    /**
     * Passo 4: valida, grava e audita autor, data e campos modificados.
     * CNPJ/CPF e tipo de perfil identificam o usuário e não mudam por atualização.
     */
    public Usuario atualizar(String documentoAdministrador, String documento, DadosUsuario dados) {
        Administrador admin = exigirAdministrador(documentoAdministrador);
        Usuario atual = buscarExistente(documento);
        validarEVerificarDuplicidade(dados, atual.getId());
        if (dados.perfil() != atual.getPerfil()) {
            throw new IllegalArgumentException("O tipo de perfil não pode ser alterado.");
        }
        if (!ValidadorUsuario.somenteDigitos(dados.documento())
                .equals(ValidadorUsuario.somenteDigitos(atual.getDocumento()))) {
            throw new IllegalArgumentException("O CNPJ/CPF não pode ser alterado.");
        }

        String nome = dados.nome().trim().replaceAll("\\s+", " ");
        String email = dados.email().trim().toLowerCase();
        String telefone = textoOuNulo(dados.telefone());
        String endereco = textoOuNulo(dados.endereco());

        List<String> campos = new ArrayList<>();
        List<String> alteracoes = new ArrayList<>();
        comparar("nome", atual.getNome(), nome, campos, alteracoes);
        comparar("email", atual.getEmail(), email, campos, alteracoes);
        comparar("telefone", atual.getTelefone(), telefone, campos, alteracoes);
        comparar("endereco", atual.getEndereco(), endereco, campos, alteracoes);
        comparar("latitude", atual.getLatitude(), dados.latitude(), campos, alteracoes);
        comparar("longitude", atual.getLongitude(), dados.longitude(), campos, alteracoes);
        if (campos.isEmpty()) {
            return atual; // nada mudou: nada a gravar
        }
        atual.setNome(nome);
        atual.setEmail(email);
        atual.setTelefone(telefone);
        atual.setEndereco(endereco);
        atual.setLocalizacao(dados.latitude(), dados.longitude());
        auditar(admin, RegistroAuditoria.Operacao.ATUALIZACAO, atual, campos, String.join("; ", alteracoes));
        return atual;
    }

    // ---------- 3c: remover (inativar) ----------

    /** Passos 2-3: verifica pendências e devolve a mensagem de confirmação. */
    @Transactional(readOnly = true)
    public String prepararInativacao(String documentoAdministrador, String documento) {
        Administrador admin = exigirAdministrador(documentoAdministrador);
        Usuario u = buscarExistente(documento);
        verificarInativacaoPermitida(admin, u);
        return "O perfil de " + u.getNome() + " (" + u.getDocumento() + ") será inativado "
                + "e o histórico de transações será preservado. Confirma a operação?";
    }

    /**
     * Passos 4-5: após a confirmação, inativa, revoga as permissões e registra a auditoria.
     * As verificações do passo 2 são refeitas, pois algo pode ter mudado desde a confirmação.
     */
    public Usuario inativar(String documentoAdministrador, String documento) {
        Administrador admin = exigirAdministrador(documentoAdministrador);
        Usuario u = buscarExistente(documento);
        verificarInativacaoPermitida(admin, u);
        u.setAtivo(false);
        u.revogarPermissoes();
        auditar(admin, RegistroAuditoria.Operacao.INATIVACAO, u, List.of("ativo"),
                "Usuário inativado; permissões revogadas; histórico preservado.");
        return u;
    }

    private void verificarInativacaoPermitida(Administrador admin, Usuario u) {
        if (!u.isAtivo()) {
            throw new IllegalStateException("O usuário já está inativo.");
        }
        if (u.getId().equals(admin.getId())) {
            throw new IllegalArgumentException("O administrador não pode inativar o próprio usuário.");
        }
        if (possuiLotesAtivosOuReservas(u)) {
            throw new IllegalStateException("O usuário possui lotes ativos ou reservas em andamento. "
                    + "Conclua ou cancele essas operações antes de inativar o perfil.");
        }
    }

    /** 3c, passo 2. Doador: lotes DISPONIVEL ou RESERVADO_PENDENTE. Receptor: reserva aguardando retirada. */
    private boolean possuiLotesAtivosOuReservas(Usuario u) {
        return switch (u.getPerfil()) {
            case DOADOR -> lotes.existsByEstabelecimentoIdAndStatusIn(u.getId(),
                    List.of(StatusLote.DISPONIVEL, StatusLote.RESERVADO_PENDENTE));
            case RECEPTOR -> reservas.existsByInstituicaoIdAndStatus(u.getId(), StatusReserva.ATIVA);
            case ADMINISTRADOR -> false;
        };
    }

    // ---------- Usado por outros módulos ----------

    /** Busca por CNPJ/CPF, com ou sem pontuação. */
    @Transactional(readOnly = true)
    public Optional<Usuario> buscarPorDocumento(String documento) {
        if (!ValidadorUsuario.documentoValido(documento)) {
            return Optional.empty();
        }
        return usuarios.findByDocumento(ValidadorUsuario.formatarDocumento(documento));
    }

    /** RF06 — Aprovar Instituição. */
    public boolean aprovarInstituicao(String documento) {
        return buscarPorDocumento(documento)
                .filter(Instituicao.class::isInstance)
                .map(u -> {
                    ((Instituicao) u).aprovar();
                    return true;
                }).orElse(false);
    }

    // ---------- Apoio ----------

    private Administrador exigirAdministrador(String documento) {
        return buscarPorDocumento(documento)
                .filter(u -> u instanceof Administrador && u.isAtivo()
                        && u.getPermissoes().contains(Perfil.Permissao.GERENCIAR_USUARIOS))
                .map(Administrador.class::cast)
                .orElseThrow(() -> new AcessoNegadoException(SO_ADMINISTRADOR));
    }

    private Usuario buscarExistente(String documento) {
        return buscarPorDocumento(documento)
                .orElseThrow(() -> new NaoEncontradoException("Usuário não encontrado: " + documento));
    }

    /** Exceções 6a (formato) e 6b (CNPJ/CPF ou e-mail já usados, inclusive por usuário inativo). */
    private void validarEVerificarDuplicidade(DadosUsuario dados, Long ignorarId) {
        Map<String, String> erros = ValidadorUsuario.validar(dados, baseValidacao);
        if (!erros.isEmpty()) {
            throw new DadosInvalidosException(erros);
        }
        usuarios.findByDocumento(ValidadorUsuario.formatarDocumento(dados.documento()))
                .filter(u -> !u.getId().equals(ignorarId))
                .ifPresent(u -> { throw new UsuarioJaCadastradoException("CNPJ/CPF", u); });
        usuarios.findByEmailIgnoreCase(dados.email().trim())
                .filter(u -> !u.getId().equals(ignorarId))
                .ifPresent(u -> { throw new UsuarioJaCadastradoException("e-mail", u); });
    }

    /** Cria a entidade do perfil escolhido, com dados padronizados e as permissões do perfil. */
    private static Usuario novaEntidade(DadosUsuario d) {
        String documento = ValidadorUsuario.formatarDocumento(d.documento());
        String nome = d.nome().trim().replaceAll("\\s+", " ");
        String email = d.email().trim().toLowerCase();
        Usuario u = switch (d.perfil()) {
            case DOADOR -> new Estabelecimento(documento, nome, email);
            case RECEPTOR -> new Instituicao(documento, nome, email);
            case ADMINISTRADOR -> new Administrador(documento, nome, email);
        };
        u.setTelefone(textoOuNulo(d.telefone()));
        u.setEndereco(textoOuNulo(d.endereco()));
        u.setLocalizacao(d.latitude(), d.longitude());
        u.vincularPermissoes(d.perfil().getPermissoes());
        return u;
    }

    private List<Usuario> todosOrdenados() {
        return usuarios.findAll().stream().sorted(Comparator.comparing(Usuario::getId)).toList();
    }

    private void auditar(Usuario autor, RegistroAuditoria.Operacao operacao, Usuario alvo,
                         List<String> campos, String descricao) {
        auditoria.save(new RegistroAuditoria(autor.getDocumento(), operacao, alvo.getId(), campos, descricao));
    }

    private static void comparar(String campo, Object antes, Object depois,
                                 List<String> campos, List<String> alteracoes) {
        if (!Objects.equals(antes, depois)) {
            campos.add(campo);
            alteracoes.add(campo + ": '" + antes + "' -> '" + depois + "'");
        }
    }

    private static String textoOuNulo(String s) {
        return vazio(s) ? null : s.trim();
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }

    private static String semAcento(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase().trim();
    }
}
