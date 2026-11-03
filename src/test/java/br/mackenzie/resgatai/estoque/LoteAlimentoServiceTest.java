package br.mackenzie.resgatai.estoque;

import static br.mackenzie.resgatai.comum.DadosDeTeste.*;
import static org.junit.jupiter.api.Assertions.*;

import br.mackenzie.resgatai.comum.DadosInvalidosException;
import br.mackenzie.resgatai.notificacao.Notificacao;
import br.mackenzie.resgatai.notificacao.NotificacaoRepository;
import br.mackenzie.resgatai.usuario.Estabelecimento;
import br.mackenzie.resgatai.usuario.Instituicao;
import br.mackenzie.resgatai.comum.RegistroAuditoria;
import br.mackenzie.resgatai.comum.RegistroAuditoriaRepository;
import br.mackenzie.resgatai.usuario.UsuarioService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** UC02 — Cadastrar Lote de Alimentos: fluxo principal, alternativos 3a/7a e exceções 5a/7a/9a. */
@SpringBootTest
@Transactional // cada teste roda numa transação desfeita ao final, então o banco começa limpo
public class LoteAlimentoServiceTest {

    @Autowired
    private LoteAlimentoService service;

    @Autowired
    private LoteAlimentoRepository lotes;

    @Autowired
    private UsuarioService usuarios;

    @Autowired
    private RegistroAuditoriaRepository auditoria;

    @Autowired
    private NotificacaoRepository notificacoes;

    @PersistenceContext
    private EntityManager em;

    private Estabelecimento doador;
    private Instant agora;

    @BeforeEach
    public void setUp() {
        agora = Instant.now().truncatedTo(ChronoUnit.MILLIS); // o banco guarda até microssegundos
        usuarios.criarAdministradorInicial(administrador());
        doador = (Estabelecimento) usuarios.cadastrar(ADMIN, doador());
    }

    private Instant horas(long n) {
        return agora.plus(Duration.ofHours(n));
    }

    /** Lote com categoria "Padaria", sem foto e com a retirada começando agora. */
    private NovoLote lote(String tipo, int quantidade, BigDecimal preco, Instant validade, Instant fimRetirada) {
        return new NovoLote(tipo, "Padaria", quantidade, "un", preco, null, validade, null, agora, fimRetirada,
                null, null, null);
    }

    private NovoLote paesValidos() {
        return paes(agora, 4, 2);
    }

    private LoteAlimento assertPublicado(String cenario, NovoLote dados) {
        System.out.println("- " + cenario);
        LoteAlimentoService.Resumo resumo = service.cadastrar(doador.getDocumento(), dados);
        System.out.println("  -> Confirmando que o lote foi gravado como DISPONIVEL.\n");
        assertNotNull(resumo.lote().getId(), cenario);
        assertEquals(StatusLote.DISPONIVEL, resumo.lote().getStatus(), cenario);
        return resumo.lote();
    }

    private void assertRecusado(String cenario, String campo, String mensagemEsperada, NovoLote dados) {
        System.out.println("- " + cenario);
        DadosInvalidosException erro = assertThrows(DadosInvalidosException.class,
                () -> service.cadastrar(doador.getDocumento(), dados), cenario);
        System.out.println("  -> Confirmando que o sistema bloqueou: " + erro.getInconsistencias() + "\n");
        assertEquals(mensagemEsperada, erro.getInconsistencias().get(campo), cenario);
        assertEquals(0, lotes.count(), cenario + ": lote recusado não pode ser gravado");
    }

    // ---------- Fluxo principal ----------

    @Test
    public void testIndicadorDeUrgenciaPeloPrazoRestante() {
        System.out.println("- Passo 6: sistema calcula o prazo restante e apresenta a urgencia.");
        assertEquals(Urgencia.CRITICA, service.indicadorUrgencia(horas(3)));
        assertEquals(Urgencia.ALTA, service.indicadorUrgencia(horas(10)));
        assertEquals(Urgencia.MEDIA, service.indicadorUrgencia(horas(20)));
        assertEquals(Urgencia.BAIXA, service.indicadorUrgencia(horas(48)));
        assertEquals(Urgencia.VENCIDO, service.indicadorUrgencia(horas(-1)));
        assertThrows(DadosInvalidosException.class, () -> service.indicadorUrgencia(null));
    }

    @Test
    public void testCadastrarLoteGravaTodosOsDados() {
        NovoLote dados = new NovoLote("Pão francês", "Padaria", 50, "un", new BigDecimal("9.90"),
                "https://exemplo.com/paes.jpg", horas(4), "Manter em local seco", horas(1), horas(3), null, null, null);
        Long id = assertPublicado("Doador cadastra lote completo, com preco, foto e observacoes.", dados).getId();

        em.flush();
        em.clear(); // força a leitura do banco em vez do cache do JPA
        LoteAlimento salvo = lotes.findById(id).orElseThrow();
        System.out.println("  -> Confirmando que o que foi gravado no banco eh o que o doador informou.\n");
        assertEquals(doador.getId(), salvo.getEstabelecimento().getId());
        assertEquals("Pão francês", salvo.getNome());
        assertEquals("Padaria", salvo.getCategoria());
        assertEquals(50, salvo.getQuantidade());
        assertEquals("un", salvo.getUnidadeMedida());
        assertEquals(0, new BigDecimal("9.90").compareTo(salvo.getPrecoReduzido()));
        assertEquals("https://exemplo.com/paes.jpg", salvo.getFoto());
        assertEquals(horas(4), salvo.getDataValidade());
        assertEquals("Manter em local seco", salvo.getCondicoesConservacao());
        assertEquals(horas(1), salvo.getInicioRetirada());
        assertEquals(horas(3), salvo.getFimRetirada());
        assertEquals(StatusLote.DISPONIVEL, salvo.getStatus());
        assertNotNull(salvo.getDataCadastro());
    }

    @Test
    public void testPasso9AssociaEnderecoDeRetiradaDoDoador() {
        LoteAlimento lote = assertPublicado("Doador cadastra lote sem informar endereco.", paesValidos());
        assertEquals(doador.getEndereco(), lote.getEnderecoRetirada());
        assertEquals(doador.getLatitude(), lote.getLatitude());
        assertEquals(doador.getLongitude(), lote.getLongitude());
    }

    @Test
    public void testPasso12ResumoDoLotePublicado() {
        LoteAlimentoService.Resumo resumo = service.cadastrar(doador.getDocumento(), paes(agora, 10, 8));
        assertEquals(Urgencia.ALTA, resumo.urgencia());
        assertTrue(resumo.prazoRestante().toHours() >= 9 && resumo.prazoRestante().toHours() <= 10);
        assertNotNull(resumo.notificacao(), "resultado do UC03 faz parte do resumo");
    }

    @Test
    public void testPasso11IncluiNotificacaoDosReceptoresProximos() {
        usuarios.cadastrar(ADMIN, ong());
        usuarios.aprovarInstituicao(ONG);

        LoteAlimentoService.Resumo resumo = service.cadastrar(doador.getDocumento(), paesValidos());

        assertEquals(1, resumo.notificacao().notificados());
        List<Notificacao> enviadas = notificacoes.findByLoteIdAndTipoOrderByDistanciaKmAsc(resumo.lote().getId(), Notificacao.Tipo.NOVO_LOTE_PROXIMO);
        assertEquals(1, enviadas.size());
        assertEquals(ONG, enviadas.get(0).getDestinatario().getDocumento());
    }

    @Test
    public void testCadastroRegistraNoLogDoSistema() {
        LoteAlimento lote = assertPublicado("Doador cadastra lote.", paesValidos());
        List<RegistroAuditoria> log = auditoria.findByUsuarioAlvoIdOrderByIdAsc(doador.getId());
        RegistroAuditoria ultimo = log.get(log.size() - 1);
        assertEquals(RegistroAuditoria.Operacao.CADASTRO_LOTE, ultimo.getOperacao());
        assertEquals(doador.getDocumento(), ultimo.getAutor());
        assertTrue(ultimo.getDescricao().contains("Lote " + lote.getId()));
    }

    @Test
    public void testCadastrarDoacaoSemPrecoNemFoto() {
        LoteAlimento lote = assertPublicado("Doador cadastra lote sem preco (doacao) e sem foto.", paesValidos());
        assertNull(lote.getPrecoReduzido());
        assertTrue(lote.isDoacao());
        assertNull(lote.getFoto());
    }

    @Test
    public void testMesmoDoadorCadastraVariosLotes() {
        LoteAlimento paes = assertPublicado("Doador cadastra o primeiro lote.", paesValidos());
        LoteAlimento tomates = assertPublicado("Doador cadastra o segundo lote.",
                lote("Tomate", 10, null, horas(12), horas(8)));
        assertNotEquals(paes.getId(), tomates.getId());
        assertEquals(2, lotes.count());
    }

    @Test
    public void testLimitesAceitos() {
        assertPublicado("Quantidade minima (1).", lote("Pão francês", 1, null, horas(4), horas(2)));
        assertPublicado("Preco zero.", lote("Pão francês", 50, BigDecimal.ZERO, horas(4), horas(2)));
        assertPublicado("Retirada termina exatamente no vencimento.", lote("Pão francês", 50, null, horas(4), horas(4)));
        Instant daquiUmMinuto = agora.plus(Duration.ofMinutes(1));
        assertPublicado("Validade daqui a 1 minuto.", lote("Pão francês", 50, null, daquiUmMinuto, daquiUmMinuto));
        assertPublicado("Sem categoria.", new NovoLote("Pão francês", null, 50, "un", horas(4), agora, horas(2)));
        assertEquals(5, lotes.count());
    }

    // ---------- 3a: cadastro a partir de lote anterior ----------

    @Test
    public void testRepetirLoteAnteriorPreencheTudoExcetoQuantidadeEValidade() {
        NovoLote original = new NovoLote("Pão francês", "Padaria", 50, "un", new BigDecimal("9.90"),
                "https://exemplo.com/paes.jpg", horas(4), "Embalado em sacos", horas(1), horas(3), null, null, null);
        LoteAlimento anterior = assertPublicado("Doador cadastra o lote de referencia.", original);
        assertPublicado("Doador cadastra outro lote.", lote("Tomate", 10, null, horas(12), horas(8)));

        System.out.println("- 3a, passo 2: sistema apresenta os lotes ja cadastrados pelo Doador.");
        List<LoteAlimento> meus = service.listarDoDoador(doador.getDocumento());
        assertEquals(2, meus.size());
        assertTrue(meus.stream().anyMatch(l -> l.getId().equals(anterior.getId())));

        System.out.println("- 3a, passo 4: formulario preenchido, exceto quantidade e validade.\n");
        NovoLote repetido = service.prepararRepeticao(doador.getDocumento(), anterior.getId());
        assertEquals("Pão francês", repetido.nome());
        assertEquals("Padaria", repetido.categoria());
        assertEquals("un", repetido.unidadeMedida());
        assertEquals(0, new BigDecimal("9.90").compareTo(repetido.precoReduzido()));
        assertEquals("https://exemplo.com/paes.jpg", repetido.foto());
        assertEquals("Embalado em sacos", repetido.condicoesConservacao());
        assertEquals(horas(1), repetido.inicioRetirada());
        assertEquals(horas(3), repetido.fimRetirada());
        assertEquals(0, repetido.quantidade(), "quantidade fica para o Doador informar");
        assertNull(repetido.dataValidade(), "validade fica para o Doador informar");
        assertNull(repetido.enderecoRetirada(), "endereco do cadastro nao vira endereco alternativo");

        // retorna ao passo 4 do fluxo principal: Doador informa quantidade e validade e confirma
        assertPublicado("Doador confirma o lote repetido.", repetido.comQuantidadeEValidade(30, horas(5)));
    }

    @Test
    public void testRepetirSoLotesDoProprioDoador() {
        LoteAlimento lote = assertPublicado("Doador cadastra lote.", paesValidos());
        usuarios.cadastrar(ADMIN, new br.mackenzie.resgatai.usuario.DadosUsuario(
                br.mackenzie.resgatai.usuario.Perfil.DOADOR, MERCADO, "Mercado Vizinho", "mercado@vizinho.com",
                "(11) 4002-8922", "Rua Augusta, 100 - São Paulo/SP"));
        assertThrows(IllegalArgumentException.class, () -> service.prepararRepeticao(MERCADO, lote.getId()));
        assertThrows(IllegalArgumentException.class, () -> service.prepararRepeticao(doador.getDocumento(), 999L));
    }

    // ---------- 7a: endereço de retirada diferente ----------

    @Test
    public void testEnderecoDeRetiradaAlternativoValeSoParaOLote() {
        NovoLote dados = paesValidos().comEnderecoRetirada("Av. Paulista, 1000 - Bela Vista", BELA_VISTA[0], BELA_VISTA[1]);
        LoteAlimento lote = assertPublicado("Doador informa endereco de retirada diferente.", dados);

        assertEquals("Av. Paulista, 1000 - Bela Vista", lote.getEnderecoRetirada());
        assertEquals(BELA_VISTA[0], lote.getLatitude());
        assertEquals("Rua da Consolação, 930 - Consolação, São Paulo/SP", doador.getEndereco(), "cadastro inalterado");

        NovoLote repetido = service.prepararRepeticao(doador.getDocumento(), lote.getId());
        assertEquals("Av. Paulista, 1000 - Bela Vista", repetido.enderecoRetirada(), "repeticao mantem o alternativo");
    }

    @Test
    public void testEnderecoAlternativoInvalido() {
        assertRecusado("Endereco alternativo incompleto.", "enderecoRetirada", "Endereço de retirada incompleto.",
                paesValidos().comEnderecoRetirada("Rua", null, null));
        assertRecusado("So latitude, sem longitude.", "localizacao", "Informe latitude e longitude juntas.",
                paesValidos().comEnderecoRetirada("Av. Paulista, 1000", -23.5, null));
    }

    // ---------- Pré-condição: quem pode cadastrar ----------

    @Test
    public void testApenasDoadorAtivoComEnderecoCadastraLote() {
        Instituicao ong = (Instituicao) usuarios.cadastrar(ADMIN, ong());

        for (String documento : new String[] {ong.getDocumento(), ADMIN, "00.000.000/0000-00", null}) {
            IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                    () -> service.cadastrar(documento, paesValidos()), "documento " + documento);
            assertEquals(LoteAlimentoService.SO_DOADOR, erro.getMessage());
        }
        usuarios.inativar(ADMIN, doador.getDocumento());
        assertEquals(LoteAlimentoService.SO_DOADOR, assertThrows(IllegalArgumentException.class,
                () -> service.cadastrar(doador.getDocumento(), paesValidos())).getMessage());
        assertEquals(0, lotes.count());
    }

    // ---------- 9a: campos obrigatórios ausentes ----------

    @Test
    public void testRejeitaCamposObrigatoriosAusentesOuInvalidos() {
        String obrigatorio = LoteAlimentoService.OBRIGATORIO;
        assertThrows(DadosInvalidosException.class, () -> service.cadastrar(doador.getDocumento(), null));
        assertRecusado("Sem tipo de alimento.", "nome", obrigatorio, lote(null, 50, null, horas(4), horas(2)));
        assertRecusado("Tipo de alimento so com espacos.", "nome", obrigatorio, lote("   ", 50, null, horas(4), horas(2)));
        assertRecusado("Quantidade zero.", "quantidade", "A quantidade deve ser maior que zero.",
                lote("Pão francês", 0, null, horas(4), horas(2)));
        assertRecusado("Quantidade negativa.", "quantidade", "A quantidade deve ser maior que zero.",
                lote("Pão francês", -5, null, horas(4), horas(2)));
        assertRecusado("Sem unidade de medida.", "unidadeMedida", obrigatorio,
                new NovoLote("Pão francês", "Padaria", 50, " ", horas(4), agora, horas(2)));
        assertRecusado("Preco negativo.", "precoReduzido", "O preço reduzido não pode ser negativo.",
                lote("Pão francês", 50, new BigDecimal("-0.01"), horas(4), horas(2)));
        assertRecusado("Sem data de validade.", "dataValidade", obrigatorio, lote("Pão francês", 50, null, null, horas(2)));
        assertRecusado("Sem inicio da retirada.", "inicioRetirada", obrigatorio,
                new NovoLote("Pão francês", "Padaria", 50, "un", horas(4), null, horas(2)));
        assertRecusado("Sem fim da retirada.", "fimRetirada", obrigatorio, lote("Pão francês", 50, null, horas(4), null));
    }

    @Test
    public void testIdentificaTodasAsInconsistenciasDeUmaVez() {
        DadosInvalidosException erro = assertThrows(DadosInvalidosException.class, () -> service.cadastrar(
                doador.getDocumento(), new NovoLote("", null, 0, null, null, null, null, null, null, null, null, null, null)));
        assertEquals(Set.of("nome", "quantidade", "unidadeMedida", "dataValidade", "inicioRetirada", "fimRetirada"),
                erro.getInconsistencias().keySet());
    }

    // ---------- 5a: data de validade inválida ----------

    @Test
    public void testRejeitaLoteVencido() {
        assertRecusado("Validade vencida ha 1 hora.", "dataValidade", LoteAlimentoService.VENCIDO,
                new NovoLote("Pão francês", "Padaria", 50, "un", horas(-1), horas(-3), horas(-2)));
        assertRecusado("Validade exatamente agora.", "dataValidade", LoteAlimentoService.VENCIDO,
                new NovoLote("Pão francês", "Padaria", 50, "un", agora, horas(-2), horas(-1)));
    }

    @Test
    public void testDoadorCorrigeAValidadeEConfirmaNovamente() {
        NovoLote vencido = lote("Pão francês", 50, null, horas(-1), horas(2));
        assertRecusado("Doador informa validade passada.", "dataValidade", LoteAlimentoService.VENCIDO, vencido);
        // 5a, passo 2: corrige só a data e confirma de novo — retorna ao passo 6 do fluxo principal
        assertPublicado("Doador corrige a validade.", vencido.comQuantidadeEValidade(50, horas(4)));
    }

    // ---------- 7a: janela de retirada posterior ao vencimento ----------

    @Test
    public void testRejeitaJanelaDeRetiradaInvalida() {
        assertRecusado("Fim da retirada antes do inicio.", "fimRetirada", LoteAlimentoService.JANELA_INVERTIDA,
                lote("Pão francês", 50, null, horas(4), horas(-1)));
        assertRecusado("Inicio e fim da retirada iguais.", "fimRetirada", LoteAlimentoService.JANELA_INVERTIDA,
                lote("Pão francês", 50, null, horas(4), agora));
        assertRecusado("Retirada termina depois do vencimento (7a).", "fimRetirada",
                LoteAlimentoService.RETIRADA_APOS_VENCIMENTO, lote("Pão francês", 50, null, horas(4), horas(6)));
        assertRecusado("Retirada termina 1 segundo depois do vencimento (7a).", "fimRetirada",
                LoteAlimentoService.RETIRADA_APOS_VENCIMENTO,
                lote("Pão francês", 50, null, horas(4), horas(4).plusSeconds(1)));
    }

    @Test
    public void testRejeitaJanelaDeRetiradaJaEncerrada() {
        assertRecusado("Validade futura, mas retirada terminou ha 1 hora.", "fimRetirada", LoteAlimentoService.JANELA_ENCERRADA,
                new NovoLote("Pão francês", "Padaria", 50, "un", horas(4), horas(-3), horas(-1)));
        assertRecusado("Retirada termina exatamente agora.", "fimRetirada", LoteAlimentoService.JANELA_ENCERRADA,
                new NovoLote("Pão francês", "Padaria", 50, "un", horas(4), horas(-1), agora));
    }

    @Test
    public void testRepetirLoteNaoReaproveitaJanelaJaPassada() {
        Instant daquiUmSegundo = agora.plusSeconds(1);
        LoteAlimento anterior = assertPublicado("Lote com janela que fecha em 1 segundo.",
                lote("Pão francês", 50, null, horas(4), daquiUmSegundo));
        while (!Instant.now().isAfter(daquiUmSegundo)) {
            Thread.onSpinWait();
        }
        NovoLote repetido = service.prepararRepeticao(doador.getDocumento(), anterior.getId());
        assertNull(repetido.inicioRetirada(), "janela passada fica em branco para o Doador informar");
        assertNull(repetido.fimRetirada());
        assertEquals("Pão francês", repetido.nome());
    }

    @Test
    public void testRejeitaCamposAcimaDoTamanhoMaximo() {
        String longo = "x".repeat(600);
        assertRecusado("Tipo de alimento com 600 caracteres.", "nome", "No máximo 150 caracteres.",
                lote(longo, 50, null, horas(4), horas(2)));
        assertRecusado("URL da foto com 600 caracteres.", "foto", "URL da foto com mais de 500 caracteres.",
                paesValidos().comPrecoEFoto(null, "https://x/" + longo));
    }

    // ---------- Busca de lotes disponíveis (RF03 / UC04-2a) ----------

    @Test
    public void testBuscaFiltraPorCategoriaPrecoValidadeERaio() {
        usuarios.cadastrar(ADMIN, ong());                          // ONG na Bela Vista, ~1,2 km da padaria
        assertPublicado("Paes a R$ 9,90.", lote("Pão francês", 50, new BigDecimal("9.90"), horas(4), horas(2)));
        assertPublicado("Tomates em doacao.", new NovoLote("Tomate", "Hortifruti", 10, "kg", horas(30), agora, horas(20)));
        LoteAlimento longe = assertPublicado("Bolo retirado em Santo Andre (~17 km).",
                lote("Bolo", 5, null, horas(12), horas(6)).comEnderecoRetirada("Santo André", SANTO_ANDRE[0], SANTO_ANDRE[1]));
        assertPublicado("Leite reservado por outro.", lote("Leite", 20, null, horas(12), horas(6)))
                .alterarStatus(StatusLote.RESERVADO_PENDENTE);

        List<LoteAlimentoService.Encontrado> todos = service.buscarDisponiveis(ONG, null, null, null, null);
        assertEquals(List.of("Pão francês", "Tomate", "Bolo"), todos.stream().map(e -> e.lote().getNome()).toList(),
                "so DISPONIVEL, ordenados pela distancia");
        assertTrue(todos.get(0).distanciaKm() < 2 && todos.get(2).distanciaKm() > 15);

        assertEquals(List.of("Tomate"), nomes(service.buscarDisponiveis(ONG, "hortifruti", null, null, null)));
        assertEquals(List.of("Tomate", "Bolo"), nomes(service.buscarDisponiveis(ONG, null, new BigDecimal("5"), null, null)));
        assertEquals(List.of("Pão francês"), nomes(service.buscarDisponiveis(ONG, null, null, horas(5), null)));
        assertEquals(List.of("Pão francês", "Tomate"), nomes(service.buscarDisponiveis(ONG, null, null, null, 10.0)));
        assertFalse(nomes(service.buscarDisponiveis(ONG, null, null, null, 10.0)).contains(longe.getNome()));
    }

    @Test
    public void testBuscaUsaRaioDeAtuacaoDoReceptorEExigeReceptor() {
        Instituicao ong = (Instituicao) usuarios.cadastrar(ADMIN, ong());
        ong.setRaioAtuacaoKm(5.0);
        assertPublicado("Paes na padaria.", paesValidos());
        assertPublicado("Bolo em Santo Andre.", paesValidos().comEnderecoRetirada("Santo André", SANTO_ANDRE[0], SANTO_ANDRE[1]));

        assertEquals(1, service.buscarDisponiveis(ONG, null, null, null, null).size(), "raio de atuacao de 5 km");
        assertEquals(2, service.buscarDisponiveis(ONG, null, null, null, 50.0).size(), "raio informado prevalece");
        assertEquals(LoteAlimentoService.SO_RECEPTOR, assertThrows(IllegalArgumentException.class,
                () -> service.buscarDisponiveis(doador.getDocumento(), null, null, null, null)).getMessage());
    }

    @Test
    public void testBuscaExcluiVencidosEForaDaJanela() {
        usuarios.cadastrar(ADMIN, ong());
        Instant daquiUmMinuto = agora.plus(Duration.ofMinutes(1));
        assertPublicado("Lote cuja janela fecha em 1 minuto.", lote("Pão francês", 50, null, horas(4), daquiUmMinuto));
        assertEquals(1, service.buscarDisponiveis(ONG, null, null, null, null).size());

        // simula a passagem do tempo: o lote continua DISPONIVEL no banco, mas nao aparece mais nas buscas (RNF02)
        LoteAlimento fechado = lotes.findAll().get(0);
        assertFalse(fechado.aptoParaReserva(daquiUmMinuto.plusSeconds(1)));
    }

    private static List<String> nomes(List<LoteAlimentoService.Encontrado> encontrados) {
        return encontrados.stream().map(e -> e.lote().getNome()).toList();
    }
}
