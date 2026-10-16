package br.mackenzie.resgatai.estoque;

import br.mackenzie.resgatai.usuario.Estabelecimento;
import br.mackenzie.resgatai.usuario.UsuarioService;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LoteAlimentoService {

    private final LoteAlimentoRepository lotes;
    private final UsuarioService usuarios;

    public LoteAlimentoService(LoteAlimentoRepository lotes, UsuarioService usuarios) {
        this.lotes = lotes;
        this.usuarios = usuarios;
    }

    /** UC02 — Cadastrar Lote de Alimentos. O lote é publicado com status DISPONIVEL. */
    public LoteAlimento cadastrar(String documentoDoador, NovoLote dados) {
        Estabelecimento doador = usuarios.buscarPorDocumento(documentoDoador)
                .filter(u -> u instanceof Estabelecimento && u.isAtivo())
                .map(Estabelecimento.class::cast)
                .orElseThrow(() -> new IllegalArgumentException("Apenas doadores ativos podem cadastrar lotes."));

        if (dados == null || dados.nome() == null || dados.nome().isBlank() || dados.quantidade() <= 0
                || dados.dataValidade() == null || dados.inicioRetirada() == null || dados.fimRetirada() == null
                || (dados.precoReduzido() != null && dados.precoReduzido().signum() < 0)) {
            throw new IllegalArgumentException("Campos obrigatórios ausentes ou inválidos."); // 9a
        }
        if (!dados.dataValidade().isAfter(Instant.now())) {
            throw new IllegalArgumentException("Data de validade já passou: lotes vencidos não são publicados."); // 5a
        }
        if (!dados.inicioRetirada().isBefore(dados.fimRetirada())) {
            throw new IllegalArgumentException("O início da janela de retirada deve ser antes do fim.");
        }
        if (dados.fimRetirada().isAfter(dados.dataValidade())) {
            throw new IllegalArgumentException("A janela de retirada termina depois do vencimento do lote."); // 7a
        }

        LoteAlimento lote = lotes.save(new LoteAlimento(doador, dados));
        // UC03 (notificar receptores próximos) entra aqui quando for especificado.
        return lote;
    }
}
