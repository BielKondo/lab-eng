package br.mackenzie.resgatai.notificacao;

import static br.mackenzie.resgatai.comum.DadosDeTeste.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import br.mackenzie.resgatai.comum.Notificador;
import br.mackenzie.resgatai.estoque.LoteAlimento;
import br.mackenzie.resgatai.estoque.LoteAlimentoService;
import br.mackenzie.resgatai.estoque.NovoLote;
import br.mackenzie.resgatai.estoque.StatusLote;
import br.mackenzie.resgatai.usuario.Instituicao;
import br.mackenzie.resgatai.usuario.UsuarioService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/** UC03 — Notificar Alimentos Próximos: fluxo principal, alternativos 3a/4a e exceções 2a/6a. */
@SpringBootTest
@Transactional
public class NotificacaoServiceTest {

    @Autowired
    private NotificacaoService service;

    @Autowired
    private LoteAlimentoService lotes;

    @Autowired
    private UsuarioService usuarios;

    @Autowired
    private NotificacaoRepository notificacoes;

    @MockitoBean
    private Notificador canal; // ator secundário simulado (e-mail e push)

    private Instant agora;

    @BeforeEach
    public void setUp() {
        agora = Instant.now();
        usuarios.criarAdministradorInicial(administrador());
        usuarios.cadastrar(ADMIN, doador());
    }

    private Instituicao receptorAprovado(String documento, String nome, double[] ponto) {
        Instituicao i = (Instituicao) usuarios.cadastrar(ADMIN, receptor(documento, nome, nome.toLowerCase().replace(" ", "") + "@x.org", ponto));
        usuarios.aprovarInstituicao(documento);
        return i;
    }

    private LoteAlimento publicar(NovoLote dados) {
        return lotes.cadastrar(PADARIA, dados).lote();
    }

    @Test
    public void testFluxoPrincipalNotificaElegiveisEmOrdemDeDistancia() {
        System.out.println("- Duas instituicoes aprovadas no raio, uma fora, uma em analise.");
        receptorAprovado(ABRIGO, "Pinheiros", PINHEIROS);
        receptorAprovado(ONG, "Bela Vista", BELA_VISTA);
        receptorAprovado(COZINHA, "Campinas", CAMPINAS);
        usuarios.cadastrar(ADMIN, receptor("22.222.222/0001-91", "Em analise", "analise@x.org", BELA_VISTA));

        System.out.println("- Lote que vence em 6h: raio de 10 km.");
        LoteAlimentoService.Resumo resumo = lotes.cadastrar(PADARIA, paes(agora, 6, 4));
        NotificacaoService.Resultado r = resumo.notificacao();
        System.out.println("  -> " + r + "\n");

        assertEquals(10, r.raioKm());
        assertFalse(r.raioAmpliado());
        assertEquals(2, r.notificados());
        assertEquals(0, r.falhas());
        assertNull(r.motivoSemEnvio());

        List<Notificacao> enviadas = notificacoes.findByLoteIdAndTipoOrderByDistanciaKmAsc(resumo.lote().getId(), Notificacao.Tipo.NOVO_LOTE_PROXIMO);
        assertEquals(List.of("Bela Vista", "Pinheiros"),
                enviadas.stream().map(n -> n.getDestinatario().getNome()).toList());
        Notificacao primeira = enviadas.get(0);
        assertEquals(Notificacao.Situacao.ENVIADA, primeira.getSituacao());
        assertTrue(primeira.getDistanciaKm() < 2);
        assertTrue(primeira.getMensagem().contains("Padaria Pão Quente") && primeira.getMensagem().contains("50 un de Pão francês"));
        assertFalse(primeira.isLida());
        verify(canal, times(2)).enviar(anyString(), startsWith("Lote disponível"), anyString()); // os outros envios são os e-mails do UC01
    }

    @Test
    public void testAlternativo4aAmpliaORaioQuandoNinguemEstaProximo() {
        receptorAprovado(COZINHA, "Santo Andre", SANTO_ANDRE); // ~17 km

        NotificacaoService.Resultado r = lotes.cadastrar(PADARIA, paes(agora, 6, 4)).notificacao(); // raio inicial 10 km
        assertTrue(r.raioAmpliado());
        assertEquals(20, r.raioKm());
        assertEquals(1, r.notificados());
    }

    @Test
    public void testAlternativo4aSemElegiveisMesmoAmpliando() {
        receptorAprovado(COZINHA, "Campinas", CAMPINAS); // ~83 km

        NotificacaoService.Resultado r = lotes.cadastrar(PADARIA, paes(agora, 1, 1)).notificacao(); // raio 5 → 10 km
        assertTrue(r.raioAmpliado());
        assertEquals(10, r.raioKm());
        assertEquals(0, r.notificados());
        assertEquals(NotificacaoService.SEM_ELEGIVEIS, r.motivoSemEnvio());
        assertEquals(0, notificacoes.count());
    }

    @Test
    public void testRaioDe30kmNaoAmpliaAlemDoTeto() {
        receptorAprovado(COZINHA, "Campinas", CAMPINAS);
        NotificacaoService.Resultado r = lotes.cadastrar(PADARIA, paes(agora, 48, 24)).notificacao(); // raio 30 → 50 km
        assertEquals(50, r.raioKm());
        assertTrue(r.raioAmpliado());
        assertEquals(0, r.notificados());
    }

    @Test
    public void testAlternativo3aLoteSemLocalizacaoNaoNotifica() {
        receptorAprovado(ONG, "Bela Vista", BELA_VISTA);
        NotificacaoService.Resultado r = lotes.cadastrar(PADARIA,
                paes(agora, 6, 4).comEnderecoRetirada("Rua sem coordenadas, 10", null, null)).notificacao();
        assertEquals(NotificacaoService.SEM_LOCALIZACAO, r.motivoSemEnvio());
        assertEquals(0, r.notificados());
        assertEquals(0, notificacoes.count());
    }

    @Test
    public void testExcecao6aFalhaDoCanalRegistraEContinua() {
        Instituicao belaVista = receptorAprovado(ONG, "Bela Vista", BELA_VISTA);
        receptorAprovado(ABRIGO, "Pinheiros", PINHEIROS);
        doThrow(new RuntimeException("push indisponível"))
                .when(canal).enviar(eq(belaVista.getEmail()), startsWith("Lote disponível"), anyString());

        LoteAlimentoService.Resumo resumo = lotes.cadastrar(PADARIA, paes(agora, 6, 4));
        NotificacaoService.Resultado r = resumo.notificacao();

        assertEquals(StatusLote.DISPONIVEL, resumo.lote().getStatus(), "o lote nao e desfeito");
        assertEquals(1, r.notificados());
        assertEquals(1, r.falhas());
        List<Notificacao> registros = notificacoes.findByLoteIdAndTipoOrderByDistanciaKmAsc(resumo.lote().getId(), Notificacao.Tipo.NOVO_LOTE_PROXIMO);
        assertEquals(2, registros.size());
        Notificacao falha = registros.stream().filter(n -> n.getSituacao() == Notificacao.Situacao.FALHA).findFirst().orElseThrow();
        assertEquals("push indisponível", falha.getMotivoFalha());
        assertEquals(belaVista.getId(), falha.getDestinatario().getId());
    }

    @Test
    public void testExcecao2aLoteNaoDisponivelNaoENotificado() {
        receptorAprovado(ONG, "Bela Vista", BELA_VISTA);
        LoteAlimento lote = publicar(paes(agora, 6, 4));
        notificacoes.deleteAll();
        lote.alterarStatus(StatusLote.RESERVADO_PENDENTE);

        NotificacaoService.Resultado r = service.notificarAlimentosProximos(lote);
        assertEquals(NotificacaoService.LOTE_INDISPONIVEL, r.motivoSemEnvio());
        assertEquals(0, notificacoes.count());
    }

    @Test
    public void testReceptorConsultaEMarcaComoLida() {
        Instituicao ong = receptorAprovado(ONG, "Bela Vista", BELA_VISTA);
        publicar(paes(agora, 6, 4));
        publicar(paes(agora, 3, 2));

        List<Notificacao> minhas = service.listar(ong);
        assertEquals(2, minhas.size());
        assertFalse(minhas.get(0).isLida());

        service.marcarComoLida(ong, minhas.get(0).getId());
        assertTrue(service.listar(ong).stream().anyMatch(Notificacao::isLida));

        Instituicao outra = receptorAprovado(ABRIGO, "Outra", CAMPINAS);
        assertThrows(IllegalArgumentException.class, () -> service.marcarComoLida(outra, minhas.get(0).getId()),
                "so o destinatario marca como lida");
    }
}
