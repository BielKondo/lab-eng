package br.mackenzie.resgatai.comum;

import br.mackenzie.resgatai.estoque.NovoLote;
import br.mackenzie.resgatai.usuario.DadosUsuario;
import br.mackenzie.resgatai.usuario.Perfil;
import java.time.Duration;
import java.time.Instant;

/** Dados válidos reutilizados pelos testes dos casos de uso (documentos com dígitos verificadores corretos). */
public final class DadosDeTeste {

    public static final String ADMIN = "529.982.247-25";
    public static final String PADARIA = "98.765.432/0001-98";
    public static final String MERCADO = "45.678.901/0001-75";
    public static final String ONG = "11.222.333/0001-81";
    public static final String ABRIGO = "33.444.555/0001-81";
    public static final String COZINHA = "12.345.678/0001-95";

    // Pontos reais de São Paulo (distâncias aproximadas até a padaria, na Consolação)
    public static final double[] CONSOLACAO = {-23.5527, -46.6549};   // padaria
    public static final double[] BELA_VISTA = {-23.5614, -46.6476};   // ~1,2 km
    public static final double[] PINHEIROS = {-23.5673, -46.6930};    // ~4,2 km
    public static final double[] SANTO_ANDRE = {-23.6639, -46.5383};  // ~17 km
    public static final double[] CAMPINAS = {-22.9056, -47.0608};     // ~83 km

    private DadosDeTeste() {}

    public static DadosUsuario administrador() {
        return new DadosUsuario(Perfil.ADMINISTRADOR, ADMIN, "Admin Principal", "admin@resgatai.com", null, null);
    }

    public static DadosUsuario doador() {
        return new DadosUsuario(Perfil.DOADOR, PADARIA, "Padaria Pão Quente", "contato@paoquente.com.br",
                "(11) 3333-4444", "Rua da Consolação, 930 - Consolação, São Paulo/SP", CONSOLACAO[0], CONSOLACAO[1]);
    }

    public static DadosUsuario receptor(String documento, String nome, String email, double[] ponto) {
        return new DadosUsuario(Perfil.RECEPTOR, documento, nome, email, "(11) 98888-7777",
                "Endereço de " + nome, ponto == null ? null : ponto[0], ponto == null ? null : ponto[1]);
    }

    public static DadosUsuario ong() {
        return receptor(ONG, "ONG Prato Cheio", "ong@pratocheio.org", BELA_VISTA);
    }

    /** Lote de pães que vence em {@code horasValidade}, com retirada de agora até {@code horasRetirada}. */
    public static NovoLote paes(Instant agora, long horasValidade, long horasRetirada) {
        return new NovoLote("Pão francês", "Padaria", 50, "un", agora.plus(Duration.ofHours(horasValidade)),
                agora, agora.plus(Duration.ofHours(horasRetirada)));
    }

    public static NovoLote paesValidos(Instant agora) {
        return paes(agora, 6, 4);
    }
}
