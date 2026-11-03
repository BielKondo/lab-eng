package br.mackenzie.resgatai.reserva;

import static br.mackenzie.resgatai.comum.DadosDeTeste.*;
import static org.junit.jupiter.api.Assertions.*;

import br.mackenzie.resgatai.estoque.LoteAlimento;
import br.mackenzie.resgatai.estoque.LoteAlimentoRepository;
import br.mackenzie.resgatai.estoque.LoteAlimentoService;
import br.mackenzie.resgatai.estoque.NovoLote;
import br.mackenzie.resgatai.estoque.StatusLote;
import br.mackenzie.resgatai.notificacao.Notificacao;
import br.mackenzie.resgatai.notificacao.NotificacaoRepository;
import br.mackenzie.resgatai.notificacao.NotificacaoService;
import br.mackenzie.resgatai.usuario.Estabelecimento;
import br.mackenzie.resgatai.usuario.Instituicao;
import br.mackenzie.resgatai.comum.RegistroAuditoria;
import br.mackenzie.resgatai.comum.RegistroAuditoriaRepository;
import br.mackenzie.resgatai.usuario.UsuarioService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** UC04 — Reservar Lote de Alimentos: fluxo principal, 2a, 4a e exceções 6a/6b. */
@SpringBootTest
@Transactional
public class ReservaServiceTest {

    @Autowired
    private ReservaService service;

    @Autowired
    private LoteAlimentoService lotes;

    @Autowired
    private LoteAlimentoRepository loteRepositorio;

    @Autowired
    private ReservaRepository reservas;

    @Autowired
    private UsuarioService usuarios;

    @Autowired
    private NotificacaoService notificacaoService;

    @Autowired
    private NotificacaoRepository notificacoes;

    @Autowired
    private RegistroAuditoriaRepository auditoria;

    private Instant agora;
    private Estabelecimento doador;
    private Instituicao ong;

    @BeforeEach
    public void setUp() {
        agora = Instant.now();
        usuarios.criarAdministradorInicial(administrador());
        doador = (Estabelecimento) usuarios.cadastrar(ADMIN, doador());
        ong = (Instituicao) usuarios.cadastrar(ADMIN, ong());
        usuarios.aprovarInstituicao(ONG);
    }

    private LoteAlimento publicar(NovoLote dados) {
        return lotes.cadastrar(PADARIA, dados).lote();
    }

    // ---------- Fluxo principal ----------

    @Test
    public void testPasso1ReceptorVisualizaANotificacaoDoLote() {
        LoteAlimento lote = publicar(paesValidos(agora));
        List<Notificacao> minhas = notificacaoService.listar(ong);
        assertEquals(1, minhas.size());
        assertEquals(lote.getId(), minhas.get(0).getLote().getId());
        assertEquals(Notificacao.Tipo.NOVO_LOTE_PROXIMO, minhas.get(0).getTipo());
    }

    @Test
    public void testPasso3ApresentaDetalhesDoLote() {
        LoteAlimento lote = publicar(paesValidos(agora));
        LoteAlimento detalhes = lotes.detalhar(lote.getId());
        assertEquals("Pão francês", detalhes.getNome());
        assertEquals(50, detalhes.getQuantidade());
        assertEquals(doador.getEndereco(), detalhes.getEnderecoRetirada());
        assertThrows(IllegalArgumentException.class, () -> lotes.detalhar(999L));
    }

    @Test
    public void testPassos7a11ReservaComPinQrPrazoENotificacaoAoDoador() {
        System.out.println("- Receptor aprovado reserva um lote DISPONIVEL.");
        LoteAlimento lote = publicar(paes(agora, 6, 4));
        int notificacoesAntes = notificacoes.findAll().size();

        Reserva r = service.reservar(ONG, lote.getId());
        System.out.println("  -> Reserva " + r.getId() + " PIN " + r.getPin() + " QR " + r.conteudoQrCode() + "\n");

        // passo 7
        assertEquals(StatusLote.RESERVADO_PENDENTE, lote.getStatus());
        assertEquals(ong.getId(), r.getInstituicao().getId());
        assertEquals(lote.getId(), r.getLote().getId());
        assertNotNull(r.getDataHora());
        assertEquals(StatusReserva.ATIVA, r.getStatus());
        // passo 8
        assertTrue(r.getPin().matches("\\d{6}"), "PIN de 6 digitos");
        assertTrue(r.getCodigoValidacao().startsWith("RESGATAI-") && r.getCodigoValidacao().length() == 25);
        assertEquals("resgatai://retirada?codigo=" + r.getCodigoValidacao(), r.conteudoQrCode());
        assertEquals(lote.getFimRetirada(), r.getPrazoLimiteRetirada(), "prazo = fim da janela do Doador");
        // passo 10
        assertTrue(lotes.buscarDisponiveis(ONG, null, null, null, null).isEmpty(), "sai das buscas");
        // passo 11
        List<Notificacao> doDoador = notificacaoService.listar(doador);
        assertEquals(notificacoesAntes + 1, notificacoes.findAll().size());
        assertEquals(Notificacao.Tipo.RESERVA_RECEBIDA, doDoador.get(0).getTipo());
        assertTrue(doDoador.get(0).getMensagem().contains("ONG Prato Cheio"));
        // auditoria
        List<RegistroAuditoria> log = auditoria.findByUsuarioAlvoIdOrderByIdAsc(ong.getId());
        assertEquals(RegistroAuditoria.Operacao.RESERVA_LOTE, log.get(log.size() - 1).getOperacao());
        // minhas reservas
        assertEquals(List.of(r.getId()), service.minhasReservas(ONG).stream().map(Reserva::getId).toList());
        assertEquals(r.getId(), service.detalhar(ONG, r.getId()).getId());
    }

    @Test
    public void testCodigosSaoUnicosPorReserva() {
        Reserva a = service.reservar(ONG, publicar(paes(agora, 6, 4)).getId());
        Reserva b = service.reservar(ONG, publicar(paes(agora, 6, 4)).getId());
        assertNotEquals(a.getCodigoValidacao(), b.getCodigoValidacao());
    }

    // ---------- 2a: localizar o lote por busca ----------

    @Test
    public void testAlternativo2aReceptorLocalizaOLotePorBuscaEReserva() {
        publicar(paes(agora, 6, 4));
        publicar(new NovoLote("Tomate", "Hortifruti", 10, "kg", agora.plus(Duration.ofHours(30)), agora,
                agora.plus(Duration.ofHours(20))));

        var encontrados = lotes.buscarDisponiveis(ONG, "Hortifruti", null, null, 10.0);
        assertEquals(1, encontrados.size());
        Reserva r = service.reservar(ONG, encontrados.get(0).lote().getId());
        assertEquals("Tomate", r.getLote().getNome());
    }

    // ---------- 4a: desistir antes de confirmar ----------

    @Test
    public void testAlternativo4aDesistirNaoAlteraOLote() {
        LoteAlimento lote = publicar(paesValidos(agora));
        lotes.detalhar(lote.getId()); // Receptor vê os detalhes e volta à listagem: nenhuma chamada de reserva
        assertEquals(StatusLote.DISPONIVEL, lote.getStatus());
        assertEquals(0, reservas.count());
    }

    // ---------- Pré-condição ----------

    @Test
    public void testApenasReceptorAtivoEAprovadoReserva() {
        LoteAlimento lote = publicar(paesValidos(agora));
        usuarios.cadastrar(ADMIN, receptor(ABRIGO, "Em analise", "analise@x.org", BELA_VISTA));

        for (String documento : new String[] {ABRIGO, PADARIA, ADMIN, "00.000.000/0000-00", null}) {
            IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                    () -> service.reservar(documento, lote.getId()), "documento " + documento);
            assertEquals(ReservaService.SO_RECEPTOR, erro.getMessage());
        }
        usuarios.inativar(ADMIN, ONG);
        assertThrows(IllegalArgumentException.class, () -> service.reservar(ONG, lote.getId()));
        assertEquals(StatusLote.DISPONIVEL, lote.getStatus());
        assertEquals(0, reservas.count());
    }

    @Test
    public void testLoteInexistente() {
        assertThrows(IllegalArgumentException.class, () -> service.reservar(ONG, 999L));
    }

    // ---------- 6a: lote já reservado por outro Receptor ----------

    @Test
    public void testExcecao6aLoteJaReservadoPorOutro() {
        System.out.println("- Duas instituicoes aprovadas; a primeira reserva o lote.");
        usuarios.cadastrar(ADMIN, receptor(ABRIGO, "Abrigo", "abrigo@x.org", PINHEIROS));
        usuarios.aprovarInstituicao(ABRIGO);
        LoteAlimento disputado = publicar(paes(agora, 6, 4));
        LoteAlimento outro = publicar(new NovoLote("Tomate", "Hortifruti", 10, "kg",
                agora.plus(Duration.ofHours(30)), agora, agora.plus(Duration.ofHours(20))));
        service.reservar(ONG, disputado.getId());

        System.out.println("- A segunda tenta reservar o mesmo lote.");
        LoteIndisponivelException erro = assertThrows(LoteIndisponivelException.class,
                () -> service.reservar(ABRIGO, disputado.getId()));
        System.out.println("  -> " + erro.getMessage() + "\n");

        assertEquals(disputado.getId(), erro.getLote().getId());
        assertTrue(erro.getMessage().contains("reservado por outra instituição"));
        assertEquals(StatusLote.RESERVADO_PENDENTE, disputado.getStatus(), "registro nao e alterado");
        assertEquals(1, reservas.count(), "nenhuma reserva nova");
        assertEquals(List.of(outro.getId()), erro.getAlternativas().stream().map(e -> e.lote().getId()).toList(),
                "apresenta os demais lotes disponiveis");
    }

    // ---------- 6b: lote vencido ou fora da janela de retirada ----------

    @Test
    public void testExcecao6bLoteForaDaJanelaViraExpiradoENotificaODoador() {
        Instant daquiUmSegundo = agora.plusSeconds(1);
        LoteAlimento lote = publicar(new NovoLote("Pão francês", "Padaria", 50, "un",
                agora.plus(Duration.ofHours(4)), agora, daquiUmSegundo));
        int notificacoesAntes = notificacaoService.listar(doador).size();
        esperarAte(daquiUmSegundo);

        LoteExpiradoException erro = assertThrows(LoteExpiradoException.class, () -> service.reservar(ONG, lote.getId()));

        assertEquals(lote.getId(), erro.getLote().getId());
        assertEquals(StatusLote.EXPIRADO, loteRepositorio.findById(lote.getId()).orElseThrow().getStatus());
        assertEquals(0, reservas.count());
        assertTrue(lotes.buscarDisponiveis(ONG, null, null, null, null).isEmpty(), "sai das listagens");
        List<Notificacao> doDoador = notificacaoService.listar(doador);
        assertEquals(notificacoesAntes + 1, doDoador.size());
        assertEquals(Notificacao.Tipo.LOTE_EXPIRADO, doDoador.get(0).getTipo());
        List<RegistroAuditoria> log = auditoria.findByUsuarioAlvoIdOrderByIdAsc(doador.getId());
        assertEquals(RegistroAuditoria.Operacao.LOTE_EXPIRADO, log.get(log.size() - 1).getOperacao());
    }

    @Test
    public void testExcecao6bLoteVencido() {
        Instant daquiUmSegundo = agora.plusSeconds(1);
        LoteAlimento lote = publicar(new NovoLote("Pão francês", "Padaria", 50, "un", daquiUmSegundo, agora, daquiUmSegundo));
        esperarAte(daquiUmSegundo);
        assertThrows(LoteExpiradoException.class, () -> service.reservar(ONG, lote.getId()));
        assertEquals(StatusLote.EXPIRADO, lote.getStatus());
    }

    @Test
    public void testLoteExpiradoNaoPodeSerReservadoNovamente() {
        Instant daquiUmSegundo = agora.plusSeconds(1);
        LoteAlimento lote = publicar(new NovoLote("Pão francês", "Padaria", 50, "un", daquiUmSegundo, agora, daquiUmSegundo));
        esperarAte(daquiUmSegundo);
        assertThrows(LoteExpiradoException.class, () -> service.reservar(ONG, lote.getId()));
        LoteIndisponivelException erro = assertThrows(LoteIndisponivelException.class,
                () -> service.reservar(ONG, lote.getId()), "ja nao esta DISPONIVEL: cai no 6a");
        assertTrue(erro.getMessage().contains("venceu ou saiu da janela"), "mensagem reflete o status real");
    }

    private static void esperarAte(Instant instante) {
        while (!Instant.now().isAfter(instante)) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
