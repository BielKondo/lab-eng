package br.mackenzie.resgatai.estoque;

import static org.junit.jupiter.api.Assertions.*;

import br.mackenzie.resgatai.usuario.Administrador;
import br.mackenzie.resgatai.usuario.Estabelecimento;
import br.mackenzie.resgatai.usuario.Instituicao;
import br.mackenzie.resgatai.usuario.UsuarioService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** UC02 — Cadastrar Lote de Alimentos. */
@SpringBootTest
@Transactional // cada teste roda numa transação desfeita ao final, então o banco começa limpo
public class LoteAlimentoServiceTest {

    private static final String SO_DOADOR = "Apenas doadores ativos podem cadastrar lotes.";
    private static final String CAMPOS_INVALIDOS = "Campos obrigatórios ausentes ou inválidos.";
    private static final String VENCIDO = "Data de validade já passou: lotes vencidos não são publicados.";
    private static final String JANELA_INVERTIDA = "O início da janela de retirada deve ser antes do fim.";
    private static final String RETIRADA_APOS_VENCIMENTO = "A janela de retirada termina depois do vencimento do lote.";

    @Autowired
    private LoteAlimentoService service;

    @Autowired
    private LoteAlimentoRepository lotes;

    @Autowired
    private UsuarioService usuarios;

    @PersistenceContext
    private EntityManager em;

    private Estabelecimento doador;
    private Instant agora;

    @BeforeEach
    public void setUp() {
        agora = Instant.now().truncatedTo(ChronoUnit.MILLIS); // o banco guarda até microssegundos
        doador = new Estabelecimento("98.765.432/0001-10", "Padaria Teste", "contato@padaria.com");
        usuarios.criar(doador);
    }

    private Instant horas(long n) {
        return agora.plus(Duration.ofHours(n));
    }

    /** Lote com categoria "Padaria", sem foto e com a retirada começando agora. */
    private NovoLote lote(String tipo, int quantidade, BigDecimal preco, Instant validade, Instant fimRetirada) {
        return new NovoLote(tipo, "Padaria", quantidade, preco, null, validade, agora, fimRetirada);
    }

    /** 50 pães que vencem em 4 horas, com retirada até daqui a 2 horas. */
    private NovoLote paesValidos() {
        return lote("Pão francês", 50, null, horas(4), horas(2));
    }

    private LoteAlimento assertPublicado(String cenario, NovoLote dados) {
        System.out.println("- " + cenario);
        LoteAlimento lote = service.cadastrar(doador.getDocumento(), dados);
        System.out.println("  -> Confirmando que o lote foi gravado como DISPONIVEL.\n");
        assertNotNull(lote.getId(), cenario);
        assertEquals(StatusLote.DISPONIVEL, lote.getStatus(), cenario);
        return lote;
    }

    private void assertRecusado(String cenario, String mensagemEsperada, String documento, NovoLote dados) {
        System.out.println("- " + cenario);
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> service.cadastrar(documento, dados), cenario);
        System.out.println("  -> Confirmando que o sistema bloqueou: " + erro.getMessage() + "\n");
        assertEquals(mensagemEsperada, erro.getMessage(), cenario);
        assertEquals(0, lotes.count(), cenario + ": lote recusado não pode ser gravado");
    }

    private void assertRecusado(String cenario, String mensagemEsperada, NovoLote dados) {
        assertRecusado(cenario, mensagemEsperada, doador.getDocumento(), dados);
    }

    // ---------- Caminhos felizes ----------

    @Test
    public void testCadastrarLoteGravaTodosOsDados() {
        NovoLote dados = new NovoLote("Pão francês", "Padaria", 50, new BigDecimal("9.90"),
                "https://exemplo.com/paes.jpg", horas(4), horas(1), horas(3));
        Long id = assertPublicado("Doador cadastra lote completo, com preco e foto.", dados).getId();

        em.flush();
        em.clear(); // força a leitura do banco em vez do cache do JPA
        LoteAlimento salvo = lotes.findById(id).orElseThrow();
        System.out.println("  -> Confirmando que o que foi gravado no banco eh o que o doador informou.\n");
        assertEquals(doador.getId(), salvo.getEstabelecimento().getId());
        assertEquals("Pão francês", salvo.getNome());
        assertEquals("Padaria", salvo.getCategoria());
        assertEquals(50, salvo.getQuantidade());
        assertEquals(0, new BigDecimal("9.90").compareTo(salvo.getPrecoReduzido()));
        assertEquals("https://exemplo.com/paes.jpg", salvo.getFoto());
        assertEquals(horas(4), salvo.getDataValidade());
        assertEquals(horas(1), salvo.getInicioRetirada());
        assertEquals(horas(3), salvo.getFimRetirada());
        assertEquals(StatusLote.DISPONIVEL, salvo.getStatus());
    }

    @Test
    public void testCadastrarDoacaoSemPrecoNemFoto() {
        LoteAlimento lote = assertPublicado("Doador cadastra lote sem preco (doacao) e sem foto.", paesValidos());
        assertNull(lote.getPrecoReduzido());
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

    // ---------- Edge cases: limites que devem ser aceitos ----------

    @Test
    public void testLimitesAceitos() {
        assertPublicado("Quantidade minima (1).", lote("Pão francês", 1, null, horas(4), horas(2)));
        assertPublicado("Preco zero.", lote("Pão francês", 50, BigDecimal.ZERO, horas(4), horas(2)));
        assertPublicado("Retirada termina exatamente no vencimento.", lote("Pão francês", 50, null, horas(4), horas(4)));
        Instant daquiUmMinuto = agora.plus(Duration.ofMinutes(1));
        assertPublicado("Validade daqui a 1 minuto.", lote("Pão francês", 50, null, daquiUmMinuto, daquiUmMinuto));
        assertPublicado("Sem categoria.", new NovoLote("Pão francês", null, 50, null, null, horas(4), agora, horas(2)));
        assertEquals(5, lotes.count());
    }

    // ---------- Erros: quem pode cadastrar ----------

    @Test
    public void testApenasDoadorAtivoCadastraLote() {
        Instituicao ong = new Instituicao("12.345.678/0001-90", "ONG Teste", "contato@ong.com");
        Administrador admin = new Administrador("123.456.789-00", "Admin", "admin@resgatai.com");
        usuarios.criar(ong);
        usuarios.criar(admin);

        assertRecusado("Instituicao tenta cadastrar lote.", SO_DOADOR, ong.getDocumento(), paesValidos());
        assertRecusado("Administrador tenta cadastrar lote.", SO_DOADOR, admin.getDocumento(), paesValidos());
        assertRecusado("Documento inexistente tenta cadastrar lote.", SO_DOADOR, "00.000.000/0000-00", paesValidos());
        assertRecusado("Documento nulo tenta cadastrar lote.", SO_DOADOR, null, paesValidos());

        usuarios.inativar(doador.getDocumento());
        assertRecusado("Doador inativo tenta cadastrar lote.", SO_DOADOR, paesValidos());
    }

    // ---------- Erros: campos obrigatórios ausentes ou inválidos (9a) ----------

    @Test
    public void testRejeitaCamposObrigatoriosAusentesOuInvalidos() {
        assertRecusado("Formulario nulo.", CAMPOS_INVALIDOS, null);
        assertRecusado("Sem tipo de alimento.", CAMPOS_INVALIDOS, lote(null, 50, null, horas(4), horas(2)));
        assertRecusado("Tipo de alimento vazio.", CAMPOS_INVALIDOS, lote("", 50, null, horas(4), horas(2)));
        assertRecusado("Tipo de alimento so com espacos.", CAMPOS_INVALIDOS, lote("   ", 50, null, horas(4), horas(2)));
        assertRecusado("Quantidade zero.", CAMPOS_INVALIDOS, lote("Pão francês", 0, null, horas(4), horas(2)));
        assertRecusado("Quantidade negativa.", CAMPOS_INVALIDOS, lote("Pão francês", -5, null, horas(4), horas(2)));
        assertRecusado("Preco negativo.", CAMPOS_INVALIDOS,
                lote("Pão francês", 50, new BigDecimal("-0.01"), horas(4), horas(2)));
        assertRecusado("Sem data de validade.", CAMPOS_INVALIDOS, lote("Pão francês", 50, null, null, horas(2)));
        assertRecusado("Sem inicio da retirada.", CAMPOS_INVALIDOS,
                new NovoLote("Pão francês", "Padaria", 50, null, null, horas(4), null, horas(2)));
        assertRecusado("Sem fim da retirada.", CAMPOS_INVALIDOS, lote("Pão francês", 50, null, horas(4), null));
    }

    // ---------- Erros: lote vencido (5a) ----------

    @Test
    public void testRejeitaLoteVencido() {
        assertRecusado("Validade vencida ha 1 hora.", VENCIDO,
                new NovoLote("Pão francês", "Padaria", 50, null, null, horas(-1), horas(-3), horas(-2)));
        assertRecusado("Validade exatamente agora.", VENCIDO,
                new NovoLote("Pão francês", "Padaria", 50, null, null, agora, horas(-2), horas(-1)));
    }

    // ---------- Erros: janela de retirada ----------

    @Test
    public void testRejeitaJanelaDeRetiradaInvalida() {
        assertRecusado("Fim da retirada antes do inicio.", JANELA_INVERTIDA,
                lote("Pão francês", 50, null, horas(4), horas(-1)));
        assertRecusado("Inicio e fim da retirada iguais.", JANELA_INVERTIDA,
                lote("Pão francês", 50, null, horas(4), agora));
        assertRecusado("Retirada termina depois do vencimento (7a).", RETIRADA_APOS_VENCIMENTO,
                lote("Pão francês", 50, null, horas(4), horas(6)));
        assertRecusado("Retirada termina 1 segundo depois do vencimento (7a).", RETIRADA_APOS_VENCIMENTO,
                lote("Pão francês", 50, null, horas(4), horas(4).plusSeconds(1)));
    }
}
