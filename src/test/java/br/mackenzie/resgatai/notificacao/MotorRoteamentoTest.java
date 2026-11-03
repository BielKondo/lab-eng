package br.mackenzie.resgatai.notificacao;

import static br.mackenzie.resgatai.comum.DadosDeTeste.*;
import static org.junit.jupiter.api.Assertions.*;

import br.mackenzie.resgatai.comum.Geo;
import br.mackenzie.resgatai.estoque.LoteAlimento;
import br.mackenzie.resgatai.estoque.NovoLote;
import br.mackenzie.resgatai.usuario.Estabelecimento;
import br.mackenzie.resgatai.usuario.Instituicao;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** UC03, passos 2-5: regra de raio e seleção de Receptores elegíveis (sem Spring). */
public class MotorRoteamentoTest {

    private static Instituicao instituicao(String documento, String nome, double[] ponto, boolean aprovada) {
        Instituicao i = new Instituicao(documento, nome, nome.toLowerCase().replace(" ", "") + "@x.org");
        if (ponto != null) i.setLocalizacao(ponto[0], ponto[1]);
        if (aprovada) i.aprovar();
        return i;
    }

    private static LoteAlimento lotePadaria(long horasValidade) {
        Estabelecimento padaria = new Estabelecimento(PADARIA, "Padaria", "p@p.com");
        Instant agora = Instant.now();
        return new LoteAlimento(padaria, new NovoLote("Pão", "Padaria", 10, "un",
                agora.plus(Duration.ofHours(horasValidade)), agora, agora.plus(Duration.ofHours(1))),
                "Consolação", CONSOLACAO[0], CONSOLACAO[1]);
    }

    @Test
    public void testRaioCresceComOTempoRestante() {
        assertEquals(5, MotorRoteamento.raioKm(Duration.ofMinutes(30)));
        assertEquals(5, MotorRoteamento.raioKm(Duration.ofHours(2)));
        assertEquals(10, MotorRoteamento.raioKm(Duration.ofHours(2).plusMinutes(1)));
        assertEquals(10, MotorRoteamento.raioKm(Duration.ofHours(6)));
        assertEquals(20, MotorRoteamento.raioKm(Duration.ofHours(12)));
        assertEquals(30, MotorRoteamento.raioKm(Duration.ofHours(13)));
        assertEquals(30, MotorRoteamento.raioKm(Duration.ofDays(3)));
    }

    @Test
    public void testAmpliacaoDoRaioRespeitaOTeto() {
        assertEquals(10, MotorRoteamento.ampliar(5));
        assertEquals(40, MotorRoteamento.ampliar(20));
        assertEquals(50, MotorRoteamento.ampliar(30), "30 km dobrado passaria do teto de 50 km");
        assertEquals(50, MotorRoteamento.ampliar(50));
    }

    @Test
    public void testDistanciaHaversine() {
        double consolacaoBelaVista = Geo.distanciaKm(CONSOLACAO[0], CONSOLACAO[1], BELA_VISTA[0], BELA_VISTA[1]);
        assertTrue(consolacaoBelaVista > 1.0 && consolacaoBelaVista < 1.5, "~1,2 km, veio " + consolacaoBelaVista);
        double consolacaoCampinas = Geo.distanciaKm(CONSOLACAO[0], CONSOLACAO[1], CAMPINAS[0], CAMPINAS[1]);
        assertTrue(consolacaoCampinas > 75 && consolacaoCampinas < 90, "~83 km, veio " + consolacaoCampinas);
        assertEquals(0, Geo.distanciaKm(CONSOLACAO[0], CONSOLACAO[1], CONSOLACAO[0], CONSOLACAO[1]), 1e-9);
    }

    @Test
    public void testElegiveisFiltraAprovacaoLocalizacaoERaioEOrdenaPorDistancia() {
        LoteAlimento lote = lotePadaria(4);
        Instituicao belaVista = instituicao(ONG, "Bela Vista", BELA_VISTA, true);
        Instituicao pinheiros = instituicao(ABRIGO, "Pinheiros", PINHEIROS, true);
        Instituicao santoAndre = instituicao(COZINHA, "Santo Andre", SANTO_ANDRE, true);
        Instituicao emAnalise = instituicao("22.222.222/0001-91", "Em analise", BELA_VISTA, false);
        Instituicao semLocal = instituicao("44.444.444/0001-91", "Sem local", null, true);
        Instituicao inativa = instituicao("55.555.555/0001-91", "Inativa", BELA_VISTA, true);
        inativa.setAtivo(false);
        List<Instituicao> todas = List.of(santoAndre, pinheiros, belaVista, emAnalise, semLocal, inativa);

        List<MotorRoteamento.Elegivel> em10km = MotorRoteamento.elegiveis(lote, todas, 10);
        assertEquals(List.of("Bela Vista", "Pinheiros"),
                em10km.stream().map(e -> e.instituicao().getNome()).toList());
        assertTrue(em10km.get(0).distanciaKm() < em10km.get(1).distanciaKm());

        assertEquals(3, MotorRoteamento.elegiveis(lote, todas, 20).size(), "Santo Andre entra com 20 km");
        assertTrue(MotorRoteamento.elegiveis(lote, todas, 1).isEmpty(), "ninguem a menos de 1 km");
    }

    @Test
    public void testElegiveisRespeitaRaioDeAtuacaoDoReceptor() {
        LoteAlimento lote = lotePadaria(4);
        Instituicao pinheiros = instituicao(ABRIGO, "Pinheiros", PINHEIROS, true);
        pinheiros.setRaioAtuacaoKm(2.0); // so busca ate 2 km, e a padaria esta a ~4 km
        assertTrue(MotorRoteamento.elegiveis(lote, List.of(pinheiros), 30).isEmpty());
        pinheiros.setRaioAtuacaoKm(5.0);
        assertEquals(1, MotorRoteamento.elegiveis(lote, List.of(pinheiros), 30).size());
    }

    @Test
    public void testLoteSemLocalizacaoNaoTemElegiveis() {
        Estabelecimento padaria = new Estabelecimento(PADARIA, "Padaria", "p@p.com");
        Instant agora = Instant.now();
        LoteAlimento semCoordenadas = new LoteAlimento(padaria, new NovoLote("Pão", "Padaria", 10, "un",
                agora.plus(Duration.ofHours(4)), agora, agora.plus(Duration.ofHours(1))), "Consolação", null, null);
        assertTrue(MotorRoteamento.distanciaKm(semCoordenadas, instituicao(ONG, "ONG", BELA_VISTA, true)).isEmpty());
        assertTrue(MotorRoteamento.elegiveis(semCoordenadas, List.of(instituicao(ONG, "ONG", BELA_VISTA, true)), 50).isEmpty());
    }
}
