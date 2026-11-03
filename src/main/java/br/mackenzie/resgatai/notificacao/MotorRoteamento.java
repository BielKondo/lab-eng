package br.mackenzie.resgatai.notificacao;

import br.mackenzie.resgatai.comum.Geo;
import br.mackenzie.resgatai.estoque.LoteAlimento;
import br.mackenzie.resgatai.usuario.Instituicao;
import br.mackenzie.resgatai.usuario.Usuario;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Motor de Roteamento (ator principal do UC03): cruza o tempo de validade restante com a
 * geolocalização para decidir o raio de alcance e quais Receptores são elegíveis.
 *
 * <p>Regra de raio adotada (o CONTEXTO_PROJETO.md, lacuna 11, não define fórmula):</p>
 * <pre>
 *   validade restante ≤ 2 h  → 5 km
 *   validade restante ≤ 6 h  → 10 km
 *   validade restante ≤ 12 h → 20 km
 *   validade restante > 12 h → 30 km
 *   sem elegíveis (UC03-4a)  → dobra o raio uma vez, até o teto de 50 km
 * </pre>
 */
public final class MotorRoteamento {

    public static final double RAIO_MAXIMO_KM = 50;

    /** Instituição selecionada e a distância dela até o ponto de retirada. */
    public record Elegivel(Instituicao instituicao, double distanciaKm) {}

    private MotorRoteamento() {}

    /** UC03, passo 3: raio de alcance em função do tempo restante até o vencimento. */
    public static double raioKm(Duration prazoRestante) {
        if (prazoRestante.compareTo(Duration.ofHours(2)) <= 0) return 5;
        if (prazoRestante.compareTo(Duration.ofHours(6)) <= 0) return 10;
        if (prazoRestante.compareTo(Duration.ofHours(12)) <= 0) return 20;
        return 30;
    }

    /** UC03, 4a: ampliação do raio quando ninguém é encontrado. */
    public static double ampliar(double raioKm) {
        return Math.min(raioKm * 2, RAIO_MAXIMO_KM);
    }

    /** Distância entre o ponto de retirada do lote e o usuário, se ambos tiverem coordenadas. */
    public static Optional<Double> distanciaKm(LoteAlimento lote, Usuario usuario) {
        if (!lote.possuiLocalizacao() || !usuario.possuiLocalizacao()) {
            return Optional.empty();
        }
        return Optional.of(Geo.distanciaKm(lote.getLatitude(), lote.getLongitude(),
                usuario.getLatitude(), usuario.getLongitude()));
    }

    /**
     * UC03, passos 4-5: instituições ativas, aprovadas (RF06), com localização, dentro do raio do lote
     * e do próprio raio de atuação; ordenadas da mais próxima para a mais distante,
     * com o score de reputação como desempate.
     */
    public static List<Elegivel> elegiveis(LoteAlimento lote, List<Instituicao> candidatas, double raioKm) {
        return candidatas.stream()
                .filter(i -> i.isAtivo() && i.isAprovada())
                .flatMap(i -> distanciaKm(lote, i).stream().map(d -> new Elegivel(i, d)))
                .filter(e -> e.distanciaKm() <= raioKm)
                .filter(e -> e.instituicao().getRaioAtuacaoKm() == null
                        || e.distanciaKm() <= e.instituicao().getRaioAtuacaoKm())
                .sorted(Comparator.comparingDouble(Elegivel::distanciaKm)
                        .thenComparing(e -> -e.instituicao().getScoreReputacao()))
                .toList();
    }
}
