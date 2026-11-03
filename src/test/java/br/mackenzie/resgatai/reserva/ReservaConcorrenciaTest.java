package br.mackenzie.resgatai.reserva;

import static br.mackenzie.resgatai.comum.DadosDeTeste.*;
import static org.junit.jupiter.api.Assertions.*;

import br.mackenzie.resgatai.estoque.LoteAlimento;
import br.mackenzie.resgatai.estoque.LoteAlimentoRepository;
import br.mackenzie.resgatai.estoque.LoteAlimentoService;
import br.mackenzie.resgatai.estoque.StatusLote;
import br.mackenzie.resgatai.notificacao.NotificacaoRepository;
import br.mackenzie.resgatai.comum.RegistroAuditoriaRepository;
import br.mackenzie.resgatai.usuario.UsuarioRepository;
import br.mackenzie.resgatai.usuario.UsuarioService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * UC04, passos 5-6 e 6a: "a corrida crítica". Várias instituições apertam "Reservar" ao mesmo tempo;
 * o bloqueio transacional garante que exatamente uma vence e as demais recebem o aviso de indisponibilidade.
 *
 * <p>Sem {@code @Transactional} de propósito: cada thread precisa da própria transação, e os dados
 * precisam estar realmente gravados para as outras threads os enxergarem. Por isso a limpeza manual.</p>
 */
@SpringBootTest
public class ReservaConcorrenciaTest {

    private static final int CONCORRENTES = 6;

    @Autowired private ReservaService service;
    @Autowired private LoteAlimentoService lotes;
    @Autowired private UsuarioService usuarios;
    @Autowired private LoteAlimentoRepository loteRepositorio;
    @Autowired private ReservaRepository reservas;
    @Autowired private NotificacaoRepository notificacoes;
    @Autowired private RegistroAuditoriaRepository auditoria;
    @Autowired private UsuarioRepository usuarioRepositorio;

    private final List<String> receptores = new ArrayList<>();
    private LoteAlimento lote;

    @BeforeEach
    public void setUp() {
        limpar();
        usuarios.criarAdministradorInicial(administrador());
        usuarios.cadastrar(ADMIN, doador());
        String[] documentos = {ONG, ABRIGO, COZINHA, "22.222.222/0001-91", "44.444.444/0001-91", "55.555.555/0001-91"};
        for (int i = 0; i < CONCORRENTES; i++) {
            usuarios.cadastrar(ADMIN, receptor(documentos[i], "Instituicao " + i, "inst" + i + "@x.org", BELA_VISTA));
            usuarios.aprovarInstituicao(documentos[i]);
            receptores.add(documentos[i]);
        }
        lote = lotes.cadastrar(PADARIA, paes(Instant.now(), 6, 4)).lote();
    }

    @AfterEach
    public void limpar() {
        reservas.deleteAll();
        notificacoes.deleteAll();
        loteRepositorio.deleteAll();
        auditoria.deleteAll();
        usuarioRepositorio.deleteAll();
        receptores.clear();
    }

    @Test
    public void testApenasAPrimeiraRequisicaoVenceACorrida() throws Exception {
        System.out.println("- " + CONCORRENTES + " instituicoes reservam o mesmo lote ao mesmo tempo.");
        ExecutorService executor = Executors.newFixedThreadPool(CONCORRENTES);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Object>> resultados = new ArrayList<>();
        for (String documento : receptores) {
            resultados.add(executor.submit(() -> {
                largada.await();
                try {
                    return service.reservar(documento, lote.getId());
                } catch (LoteIndisponivelException e) {
                    return e;
                }
            }));
        }
        largada.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "as reservas nao terminaram a tempo");

        int vencedores = 0;
        int avisados = 0;
        for (Future<Object> f : resultados) {
            Object r = f.get();
            if (r instanceof Reserva) vencedores++;
            else if (r instanceof LoteIndisponivelException) avisados++;
            else fail("resultado inesperado: " + r);
        }
        System.out.println("  -> " + vencedores + " reserva(s) concedida(s), " + avisados + " aviso(s) de indisponibilidade.\n");

        assertEquals(1, vencedores, "exatamente uma requisicao vence");
        assertEquals(CONCORRENTES - 1, avisados, "as demais sao informadas (6a)");
        assertEquals(1, reservas.count(), "uma unica reserva gravada");
        assertEquals(StatusLote.RESERVADO_PENDENTE, loteRepositorio.findById(lote.getId()).orElseThrow().getStatus());
    }
}
