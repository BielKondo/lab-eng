package br.mackenzie.resgatai.usuario;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.mackenzie.resgatai.comum.Notificador;
import br.mackenzie.resgatai.comum.RegistroAuditoria;
import br.mackenzie.resgatai.comum.DadosInvalidosException;
import br.mackenzie.resgatai.estoque.LoteAlimento;
import br.mackenzie.resgatai.estoque.LoteAlimentoService;
import br.mackenzie.resgatai.estoque.NovoLote;
import br.mackenzie.resgatai.reserva.Reserva;
import br.mackenzie.resgatai.reserva.ReservaService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/** UC01 — Gerenciar Usuários (CRUD): fluxo principal, alternativos 3a/3b/3c e exceções 6a/6b. */
@SpringBootTest
@Transactional // cada teste roda numa transação desfeita ao final, então o banco começa limpo
public class UsuarioServiceTest {

    // Documentos com dígitos verificadores válidos
    private static final String CPF_ADMIN = "529.982.247-25";
    private static final String CPF_OUTRO_ADMIN = "111.444.777-35";
    private static final String CPF_PESSOA = "123.456.789-09";
    private static final String CNPJ_DOADOR = "98.765.432/0001-98";
    private static final String CNPJ_ONG = "11.222.333/0001-81";
    private static final String CNPJ_ABRIGO = "33.444.555/0001-81";
    private static final String ENDERECO = "Rua da Consolação, 930 - Consolação, São Paulo/SP";

    @Autowired
    private UsuarioService service;

    @Autowired
    private LoteAlimentoService lotes;

    @Autowired
    private ReservaService reservas;

    // Atores secundários simulados
    @MockitoBean
    private Notificador email;

    @MockitoBean
    private BaseValidacaoInstituicoes baseValidacao;

    private Administrador admin;

    @BeforeEach
    public void setUp() {
        when(baseValidacao.instituicaoRegular(anyString())).thenReturn(true);
        admin = service.criarAdministradorInicial(administrador(CPF_ADMIN, "admin@resgatai.com"));
    }

    private static DadosUsuario doador() {
        return new DadosUsuario(Perfil.DOADOR, CNPJ_DOADOR, "Supermercado Teste", "contato@teste.com",
                "(11) 3333-4444", ENDERECO);
    }

    private static DadosUsuario receptor() {
        return new DadosUsuario(Perfil.RECEPTOR, CNPJ_ONG, "ONG Teste", "contato@ong.com",
                "(11) 98888-7777", ENDERECO);
    }

    private static DadosUsuario administrador(String cpf, String email) {
        return new DadosUsuario(Perfil.ADMINISTRADOR, cpf, "Admin " + cpf, email, null, null);
    }

    private static DadosUsuario comEmail(DadosUsuario d, String email) {
        return new DadosUsuario(d.perfil(), d.documento(), d.nome(), email, d.telefone(), d.endereco());
    }

    private static DadosUsuario comDocumento(DadosUsuario d, String documento) {
        return new DadosUsuario(d.perfil(), documento, d.nome(), d.email(), d.telefone(), d.endereco());
    }

    private String doc() {
        return admin.getDocumento();
    }

    private RegistroAuditoria ultimoRegistro(Usuario alvo) {
        List<RegistroAuditoria> historico = service.detalhar(doc(), alvo.getDocumento()).historico();
        return historico.get(historico.size() - 1);
    }

    // ---------- Cenarios originais da equipe, adaptados ao fluxo completo ----------

    @Test
    public void testGerenciarUsuariosCriarCamposObrigatorios() {
        System.out.println("- Tentando criar usuario com dados validos.");
        Usuario criado = service.cadastrar(doc(), doador());
        System.out.println("  -> Validacao 1: Confirmando que o usuario foi cadastrado com sucesso.\n");
        assertNotNull(criado.getId());

        System.out.println("- Tentando cadastrar um usuario nulo.");
        System.out.println("  -> Validacao 2: Confirmando que o sistema bloqueou o cadastro nulo.\n");
        assertThrows(DadosInvalidosException.class, () -> service.cadastrar(doc(), null));

        System.out.println("- Tentando cadastrar um usuario sem e-mail.");
        DadosInvalidosException erro = assertThrows(DadosInvalidosException.class,
                () -> service.cadastrar(doc(), comEmail(receptor(), " ")));
        System.out.println("  -> Validacao 3: Confirmando que o sistema bloqueou o cadastro incompleto.\n");
        assertEquals(Set.of("email"), erro.getInconsistencias().keySet());
    }

    @Test
    public void testGerenciarUsuariosCriarValidoEDuplicado() {
        System.out.println("- Cadastrando o primeiro usuario no sistema.");
        Usuario primeiro = service.cadastrar(doc(), doador());
        System.out.println("  -> Validacao 1: Confirmando que o primeiro usuario foi cadastrado.\n");
        assertNotNull(primeiro.getId());

        System.out.println("- Tentando cadastrar segundo usuario com o mesmo CNPJ/CPF.");
        UsuarioJaCadastradoException porDocumento = assertThrows(UsuarioJaCadastradoException.class,
                () -> service.cadastrar(doc(), comEmail(doador(), "outro@email.com")));
        System.out.println("  -> Validacao 2: Verificando que o cadastro duplicado foi rejeitado.\n");
        assertEquals("CNPJ/CPF", porDocumento.getCampo());

        System.out.println("- Tentando cadastrar segundo usuario com o mesmo e-mail.");
        UsuarioJaCadastradoException porEmail = assertThrows(UsuarioJaCadastradoException.class,
                () -> service.cadastrar(doc(), comDocumento(doador(), CNPJ_ABRIGO)));
        System.out.println("  -> Validacao 3: Verificando que o cadastro duplicado foi rejeitado.\n");
        assertEquals("e-mail", porEmail.getCampo());
    }

    @Test
    public void testGerenciarUsuariosInativar() {
        System.out.println("- Cadastrando usuario inicial.");
        Usuario usuario = service.cadastrar(doc(), doador());
        System.out.println("  -> Validacao 1: Confirmando que o usuario foi criado e esta ativo por padrao.\n");
        assertTrue(usuario.isAtivo());

        System.out.println("- Executando comando de inativacao por CPF/CNPJ.");
        service.inativar(doc(), usuario.getDocumento());
        System.out.println("  -> Validacao 2: Confirmando que o status do usuario mudou para inativo (false).\n");
        assertFalse(usuario.isAtivo());
    }

    @Test
    public void testAprovarInstituicaoSucessoEErro() {
        Instituicao ong = (Instituicao) service.cadastrar(doc(), receptor());
        Usuario doador = service.cadastrar(doc(), doador());

        System.out.println("- Verificando se instituicao esta valida para aprovacao.");
        assertEquals(Instituicao.StatusAnalise.EM_ANALISE, ong.getStatusAnalise());
        boolean aprovado = service.aprovarInstituicao(ong.getDocumento());
        System.out.println("  -> Validacao 1: Verificando se a aprovacao foi concedida.\n");
        assertTrue(aprovado);
        assertEquals(Instituicao.StatusAnalise.APROVADA, ong.getStatusAnalise());

        System.out.println("- Tentando aprovar um usuario que nao eh instituicao.");
        System.out.println("  -> Validacao 2: Verificando se o sistema bloqueou a aprovacao.\n");
        assertFalse(service.aprovarInstituicao(doador.getDocumento()));

        System.out.println("- Tentando aprovar um documento inexistente.");
        System.out.println("  -> Validacao 3: Verificando se o sistema bloqueou a aprovacao.\n");
        assertFalse(service.aprovarInstituicao("00.000.000/0000-00"));
    }

    @Test
    public void testGerenciarUsuariosConsultarEAtualizar() {
        System.out.println("- Cadastrando usuario inicial.");
        Usuario usuario = service.cadastrar(doc(), doador());

        System.out.println("- Buscando usuario pelo CPF/CNPJ.");
        Usuario encontrado = service.buscarPorDocumento(CNPJ_DOADOR).orElse(null);
        System.out.println("  -> Validacao 1: Verificando se o usuario foi localizado com o nome correto.\n");
        assertNotNull(encontrado);
        assertEquals(usuario.getNome(), encontrado.getNome());

        System.out.println("- Atualizando o nome do usuario cadastrado.");
        DadosUsuario form = service.prepararAtualizacao(doc(), CNPJ_DOADOR);
        service.atualizar(doc(), CNPJ_DOADOR, new DadosUsuario(form.perfil(), form.documento(),
                "Supermercado Teste Novo Nome", form.email(), form.telefone(), form.endereco()));
        System.out.println("  -> Validacao 2: Confirmando alteracao e o novo nome modificado.\n");
        assertEquals("Supermercado Teste Novo Nome", encontrado.getNome());
    }

    // ---------- Pre-condicao: administrador autenticado com perfil ativo ----------

    @Test
    public void testSomenteAdministradorAtivoGerenciaUsuarios() {
        Usuario doador = service.cadastrar(doc(), doador());
        Usuario ong = service.cadastrar(doc(), receptor());
        Usuario outroAdmin = service.cadastrar(doc(), administrador(CPF_OUTRO_ADMIN, "maria@resgatai.com"));

        for (String operador : new String[] {null, "", "00.000.000/0000-00", CPF_PESSOA,
                doador.getDocumento(), ong.getDocumento()}) {
            IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                    () -> service.listar(operador), "operador " + operador);
            assertEquals(UsuarioService.SO_ADMINISTRADOR, erro.getMessage());
        }

        assertEquals(4, service.listar(CPF_OUTRO_ADMIN).size());
        service.inativar(doc(), outroAdmin.getDocumento());
        assertThrows(IllegalArgumentException.class, () -> service.listar(CPF_OUTRO_ADMIN),
                "administrador inativado perde o acesso");
    }

    @Test
    public void testAdministradorInicialSoUmaVez() {
        assertThrows(IllegalStateException.class,
                () -> service.criarAdministradorInicial(administrador(CPF_OUTRO_ADMIN, "maria@resgatai.com")));
    }

    // ---------- Fluxo principal: criar ----------

    @Test
    public void testListarUsuariosCadastrados() {
        service.cadastrar(doc(), doador());
        service.cadastrar(doc(), receptor());
        assertEquals(List.of(CPF_ADMIN, CNPJ_DOADOR, CNPJ_ONG),
                service.listar(doc()).stream().map(Usuario::getDocumento).toList());
    }

    @Test
    public void testCadastroCriaPerfilCorretoEPadronizaDados() {
        Usuario u = service.cadastrar(doc(), new DadosUsuario(Perfil.DOADOR, "98765432000198",
                "  Supermercado   Teste ", "  Contato@Teste.COM ", "(11) 3333-4444", ENDERECO));

        assertInstanceOf(Estabelecimento.class, u);
        assertInstanceOf(Instituicao.class, service.cadastrar(doc(), receptor()));
        assertEquals(CNPJ_DOADOR, u.getDocumento());
        assertEquals("Supermercado Teste", u.getNome());
        assertEquals("contato@teste.com", u.getEmail());
        assertTrue(u.isAtivo());
    }

    @Test
    public void testCadastroVinculaPermissoesEInicializaScore() {
        Usuario doador = service.cadastrar(doc(), doador());
        Usuario ong = service.cadastrar(doc(), receptor());

        assertEquals(Perfil.DOADOR.getPermissoes(), doador.getPermissoes());
        assertEquals(Perfil.RECEPTOR.getPermissoes(), ong.getPermissoes());
        assertFalse(ong.getPermissoes().contains(Perfil.Permissao.GERENCIAR_USUARIOS));
        assertEquals(Usuario.SCORE_REPUTACAO_INICIAL, doador.getScoreReputacao());
    }

    @Test
    public void testCadastroEnviaEmailDePrimeiroAcesso() {
        Usuario u = service.cadastrar(doc(), doador());

        ArgumentCaptor<String> corpo = ArgumentCaptor.forClass(String.class);
        verify(email).enviar(org.mockito.ArgumentMatchers.eq("contato@teste.com"), anyString(), corpo.capture());
        assertNotNull(u.getTokenPrimeiroAcesso());
        assertTrue(corpo.getValue().contains(UsuarioService.URL_PRIMEIRO_ACESSO + u.getTokenPrimeiroAcesso()));
    }

    @Test
    public void testCadastroRegistraAuditoria() {
        Usuario u = service.cadastrar(doc(), doador());
        RegistroAuditoria r = ultimoRegistro(u);
        assertEquals(RegistroAuditoria.Operacao.CADASTRO, r.getOperacao());
        assertEquals(CPF_ADMIN, r.getAutor());
        assertEquals(u.getId(), r.getUsuarioAlvoId());
        assertNotNull(r.getDataHora());
    }

    @Test
    public void testFalhaNoEmailInterrompeOCadastro() {
        doThrow(new RuntimeException("SMTP fora do ar")).when(email).enviar(anyString(), anyString(), anyString());
        IllegalStateException erro = assertThrows(IllegalStateException.class,
                () -> service.cadastrar(doc(), doador()));
        assertEquals("SMTP fora do ar", erro.getCause().getMessage());
        // a exceção faz o @Transactional desfazer a gravação do usuário
    }

    // ---------- 3a: consultar ----------

    @Test
    public void testConsultarPorCriterios() {
        Usuario doador = service.cadastrar(doc(), doador());
        Usuario ong = service.cadastrar(doc(), receptor());
        Usuario abrigo = service.cadastrar(doc(), new DadosUsuario(Perfil.RECEPTOR, CNPJ_ABRIGO,
                "Cozinha Solidária São João", "cozinha@sj.org", "11 4002-8922", ENDERECO));
        service.inativar(doc(), ong.getDocumento());

        assertEquals(List.of(abrigo), service.consultar(doc(), "solidaria SAO", null, null, null));
        assertEquals(List.of(doador), service.consultar(doc(), null, "98765432000198", null, null));
        assertEquals(List.of(ong, abrigo), service.consultar(doc(), null, null, Perfil.RECEPTOR, null));
        assertEquals(List.of(ong), service.consultar(doc(), null, null, null, false));
        assertEquals(List.of(abrigo), service.consultar(doc(), null, null, Perfil.RECEPTOR, true));
        assertEquals(4, service.consultar(doc(), null, null, null, null).size());
        assertTrue(service.consultar(doc(), "inexistente", null, null, null).isEmpty());
    }

    @Test
    public void testDetalhesComPerfilScoreEHistorico() {
        Usuario doador = service.cadastrar(doc(), doador());
        DadosUsuario form = service.prepararAtualizacao(doc(), CNPJ_DOADOR);
        service.atualizar(doc(), CNPJ_DOADOR, new DadosUsuario(form.perfil(), form.documento(), form.nome(),
                form.email(), "11 4444-5555", form.endereco()));

        UsuarioService.Detalhes d = service.detalhar(doc(), CNPJ_DOADOR);

        assertSame(doador, d.usuario());
        assertEquals(Perfil.DOADOR, d.perfil());
        assertEquals(Perfil.DOADOR.getPermissoes(), d.permissoes());
        assertEquals(Usuario.SCORE_REPUTACAO_INICIAL, d.scoreReputacao());
        assertEquals(List.of(RegistroAuditoria.Operacao.CADASTRO, RegistroAuditoria.Operacao.ATUALIZACAO),
                d.historico().stream().map(RegistroAuditoria::getOperacao).toList());
        assertThrows(IllegalArgumentException.class, () -> service.detalhar(doc(), CNPJ_ABRIGO));
    }

    // ---------- 3b: atualizar ----------

    @Test
    public void testAtualizarRegistraAutorDataECampos() {
        Usuario doador = service.cadastrar(doc(), doador());
        DadosUsuario form = service.prepararAtualizacao(doc(), CNPJ_DOADOR);
        assertEquals(doador(), form, "formulario vem preenchido com os dados atuais");

        service.atualizar(doc(), CNPJ_DOADOR, new DadosUsuario(form.perfil(), form.documento(),
                "Supermercado Teste Ltda", "novo@teste.com", form.telefone(), form.endereco()));

        assertEquals("Supermercado Teste Ltda", doador.getNome());
        assertEquals("novo@teste.com", doador.getEmail());
        RegistroAuditoria r = ultimoRegistro(doador);
        assertEquals(RegistroAuditoria.Operacao.ATUALIZACAO, r.getOperacao());
        assertEquals(CPF_ADMIN, r.getAutor());
        assertNotNull(r.getDataHora());
        assertEquals(List.of("nome", "email"), r.getCamposModificados());
        assertTrue(r.getDescricao().contains("'Supermercado Teste' -> 'Supermercado Teste Ltda'"));
    }

    @Test
    public void testAtualizarSemAlteracoesNaoAudita() {
        Usuario doador = service.cadastrar(doc(), doador());
        service.atualizar(doc(), CNPJ_DOADOR, service.prepararAtualizacao(doc(), CNPJ_DOADOR));
        assertEquals(1, service.detalhar(doc(), doador.getDocumento()).historico().size());
    }

    @Test
    public void testAtualizarComDadosInvalidosNaoGrava() {
        Usuario doador = service.cadastrar(doc(), doador());
        DadosInvalidosException erro = assertThrows(DadosInvalidosException.class, () -> service.atualizar(doc(),
                CNPJ_DOADOR, new DadosUsuario(Perfil.DOADOR, CNPJ_DOADOR, "", "sem-arroba", "123", ENDERECO)));
        assertEquals(Set.of("nome", "email", "telefone"), erro.getInconsistencias().keySet());
        assertEquals("Supermercado Teste", doador.getNome());
    }

    @Test
    public void testAtualizarComEmailDeOutroUsuarioERejeitado() {
        service.cadastrar(doc(), doador());
        Usuario ong = service.cadastrar(doc(), receptor());
        UsuarioJaCadastradoException erro = assertThrows(UsuarioJaCadastradoException.class,
                () -> service.atualizar(doc(), CNPJ_DOADOR, comEmail(doador(), "CONTATO@ong.com")));
        assertSame(ong, erro.getUsuarioExistente());
    }

    @Test
    public void testAtualizarNaoMudaPerfilNemDocumento() {
        service.cadastrar(doc(), doador());
        DadosUsuario comoReceptor = new DadosUsuario(Perfil.RECEPTOR, CNPJ_DOADOR, "Supermercado Teste",
                "contato@teste.com", "(11) 3333-4444", ENDERECO);
        assertThrows(IllegalArgumentException.class, () -> service.atualizar(doc(), CNPJ_DOADOR, comoReceptor));
        assertThrows(IllegalArgumentException.class,
                () -> service.atualizar(doc(), CNPJ_DOADOR, comDocumento(doador(), CNPJ_ABRIGO)));
    }

    // ---------- 3c: remover (inativar) ----------

    @Test
    public void testInativarPedeConfirmacaoAntes() {
        Usuario doador = service.cadastrar(doc(), doador());
        String mensagem = service.prepararInativacao(doc(), CNPJ_DOADOR);
        assertTrue(mensagem.contains("será inativado"));
        assertTrue(mensagem.contains("histórico de transações será preservado"));
        assertTrue(doador.isAtivo(), "nada muda antes da confirmação");
    }

    @Test
    public void testInativarRevogaPermissoesEPreservaRegistro() {
        Usuario doador = service.cadastrar(doc(), doador());
        service.inativar(doc(), CNPJ_DOADOR);

        assertFalse(doador.isAtivo());
        assertTrue(doador.getPermissoes().isEmpty());
        assertNull(doador.getTokenPrimeiroAcesso());
        assertTrue(service.listar(doc()).contains(doador), "remoção é lógica");
        assertEquals(RegistroAuditoria.Operacao.INATIVACAO, ultimoRegistro(doador).getOperacao());
    }

    @Test
    public void testInativarBloqueadoComLoteAtivo() {
        Usuario doador = service.cadastrar(doc(), doador());
        Instant agora = Instant.now();
        lotes.cadastrar(CNPJ_DOADOR, new NovoLote("Pão francês", "Padaria", 50, "un",
                agora.plus(Duration.ofHours(4)), agora, agora.plus(Duration.ofHours(2))));

        assertThrows(IllegalStateException.class, () -> service.prepararInativacao(doc(), CNPJ_DOADOR));
        assertThrows(IllegalStateException.class, () -> service.inativar(doc(), CNPJ_DOADOR));
        assertTrue(doador.isAtivo());
    }

    @Test
    public void testInativarBloqueadoComReservaEmAndamento() {
        service.cadastrar(doc(), doador());
        Instituicao ong = (Instituicao) service.cadastrar(doc(), receptor());
        service.aprovarInstituicao(CNPJ_ONG); // só instituições aprovadas reservam (RF06)
        Instant agora = Instant.now();
        LoteAlimento lote = lotes.cadastrar(CNPJ_DOADOR, new NovoLote("Pão francês", "Padaria", 50, "un",
                agora.plus(Duration.ofHours(4)), agora, agora.plus(Duration.ofHours(2)))).lote();
        Reserva reserva = reservas.reservar(CNPJ_ONG, lote.getId()); // UC04

        assertThrows(IllegalStateException.class, () -> service.inativar(doc(), CNPJ_ONG));
        assertTrue(ong.isAtivo());

        reserva.expirarTimeout(); // reserva encerrada (UC05): já pode inativar
        service.inativar(doc(), CNPJ_ONG);
        assertFalse(ong.isAtivo());
    }

    @Test
    public void testInativarRegrasAdicionais() {
        service.cadastrar(doc(), doador());
        service.inativar(doc(), CNPJ_DOADOR);
        assertThrows(IllegalStateException.class, () -> service.inativar(doc(), CNPJ_DOADOR), "já inativo");
        assertThrows(IllegalArgumentException.class, () -> service.inativar(doc(), CPF_ADMIN), "a si mesmo");
        assertThrows(IllegalArgumentException.class, () -> service.inativar(doc(), CNPJ_ABRIGO), "inexistente");
    }

    // ---------- 6a: dados obrigatórios ausentes ou inválidos ----------

    @Test
    public void testIdentificaCadaInconsistencia() {
        DadosInvalidosException erro = assertThrows(DadosInvalidosException.class,
                () -> service.cadastrar(doc(), new DadosUsuario(null, "123", "", "email-invalido", null, null)));
        assertEquals(List.of("perfil", "documento", "nome", "email"), List.copyOf(erro.getInconsistencias().keySet()));
        verify(email, never()).enviar(anyString(), anyString(), anyString());
        assertEquals(1, service.listar(doc()).size(), "nada foi gravado");
    }

    @Test
    public void testDigitoVerificadorInvalidoECorrecao() {
        DadosUsuario comErro = comDocumento(doador(), "98.765.432/0001-10");
        DadosInvalidosException erro = assertThrows(DadosInvalidosException.class,
                () -> service.cadastrar(doc(), comErro));
        assertEquals(Set.of("documento"), erro.getInconsistencias().keySet());

        // o administrador corrige só o campo apontado e confirma de novo
        assertNotNull(service.cadastrar(doc(), comDocumento(comErro, CNPJ_DOADOR)).getId());
    }

    @Test
    public void testDoadorEReceptorExigemTelefoneEEndereco() {
        DadosInvalidosException erro = assertThrows(DadosInvalidosException.class, () -> service.cadastrar(doc(),
                new DadosUsuario(Perfil.RECEPTOR, CNPJ_ONG, "ONG Teste", "contato@ong.com", null, " ")));
        assertEquals(Set.of("telefone", "endereco"), erro.getInconsistencias().keySet());
        assertDoesNotThrow(() -> service.cadastrar(doc(), administrador(CPF_OUTRO_ADMIN, "maria@resgatai.com")),
                "administrador não precisa de telefone e endereço");
    }

    @Test
    public void testInstituicaoIrregularNaBaseDeValidacao() {
        when(baseValidacao.instituicaoRegular("11222333000181")).thenReturn(false);
        DadosInvalidosException erro = assertThrows(DadosInvalidosException.class,
                () -> service.cadastrar(doc(), receptor()));
        assertTrue(erro.getInconsistencias().get("documento").contains("Base de Validação de Instituições"));
        assertDoesNotThrow(() -> service.cadastrar(doc(), comDocumento(doador(), CNPJ_ONG)),
                "doador não depende da base de instituições");
    }

    // ---------- 6b: CNPJ/CPF ou e-mail já cadastrado ----------

    @Test
    public void testDuplicidadeApresentaRegistroExistente() {
        Usuario existente = service.cadastrar(doc(), doador());

        UsuarioJaCadastradoException porDocumento = assertThrows(UsuarioJaCadastradoException.class,
                () -> service.cadastrar(doc(), comEmail(comDocumento(receptor(), "98765432000198"), "x@x.com")));
        assertSame(existente, porDocumento.getUsuarioExistente());

        UsuarioJaCadastradoException porEmail = assertThrows(UsuarioJaCadastradoException.class,
                () -> service.cadastrar(doc(), comEmail(receptor(), "CONTATO@teste.com")));
        assertSame(existente, porEmail.getUsuarioExistente());
    }

    @Test
    public void testDuplicidadeOptaPorAtualizarExistente() {
        Usuario existente = service.cadastrar(doc(), doador());
        UsuarioJaCadastradoException erro = assertThrows(UsuarioJaCadastradoException.class,
                () -> service.cadastrar(doc(), comEmail(doador(), "novo@teste.com")));

        String documento = erro.getUsuarioExistente().getDocumento();
        DadosUsuario form = service.prepararAtualizacao(doc(), documento);
        service.atualizar(doc(), documento, comEmail(form, "novo@teste.com"));

        assertEquals("novo@teste.com", existente.getEmail());
        assertEquals(2, service.listar(doc()).size());
    }

    @Test
    public void testUsuarioInativoContinuaBloqueandoODocumento() {
        service.cadastrar(doc(), doador());
        service.inativar(doc(), CNPJ_DOADOR);
        assertThrows(UsuarioJaCadastradoException.class, () -> service.cadastrar(doc(), doador()));
    }
}
