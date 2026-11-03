package br.mackenzie.resgatai;

import br.mackenzie.resgatai.estoque.LoteAlimentoService;
import br.mackenzie.resgatai.estoque.NovoLote;
import br.mackenzie.resgatai.usuario.DadosUsuario;
import br.mackenzie.resgatai.usuario.Instituicao;
import br.mackenzie.resgatai.usuario.Perfil;
import br.mackenzie.resgatai.usuario.UsuarioRepository;
import br.mackenzie.resgatai.usuario.UsuarioService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dados de demonstração para o front-end (ativado por {@code resgatai.dados-iniciais=true}
 * no application.properties; desligado nos testes). Cria o administrador inicial, um Doador,
 * duas Instituições (uma aprovada, uma em análise) e um lote, o que já dispara o UC03.
 */
@Component
@ConditionalOnProperty(name = "resgatai.dados-iniciais", havingValue = "true")
public class DadosIniciais implements CommandLineRunner {

    public static final String ADMIN = "529.982.247-25";
    public static final String PADARIA = "98.765.432/0001-98";
    public static final String ONG = "11.222.333/0001-81";
    public static final String ABRIGO = "33.444.555/0001-81";

    private static final Logger log = LoggerFactory.getLogger(DadosIniciais.class);

    private final UsuarioService usuarios;
    private final UsuarioRepository repositorio;
    private final LoteAlimentoService lotes;

    public DadosIniciais(UsuarioService usuarios, UsuarioRepository repositorio, LoteAlimentoService lotes) {
        this.usuarios = usuarios;
        this.repositorio = repositorio;
        this.lotes = lotes;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (repositorio.count() > 0) {
            return;
        }
        usuarios.criarAdministradorInicial(new DadosUsuario(Perfil.ADMINISTRADOR, ADMIN, "Administrador",
                "admin@resgatai.app", null, null));
        usuarios.cadastrar(ADMIN, new DadosUsuario(Perfil.DOADOR, PADARIA, "Padaria Pão Quente",
                "contato@paoquente.com.br", "(11) 3333-4444",
                "Rua da Consolação, 930 - Consolação, São Paulo/SP", -23.5527, -46.6549));
        usuarios.cadastrar(ADMIN, new DadosUsuario(Perfil.RECEPTOR, ONG, "ONG Prato Cheio",
                "ong@pratocheio.org", "(11) 98888-7777",
                "Rua Treze de Maio, 500 - Bela Vista, São Paulo/SP", -23.5614, -46.6476));
        usuarios.cadastrar(ADMIN, new DadosUsuario(Perfil.RECEPTOR, ABRIGO, "Abrigo Esperança",
                "abrigo@esperanca.org", "(11) 97777-6666",
                "Av. Paulista, 1000 - Bela Vista, São Paulo/SP", -23.5658, -46.6500));
        usuarios.aprovarInstituicao(ONG); // RF06: só a ONG aprovada pode reservar
        repositorio.findByDocumento(ONG).ifPresent(u -> ((Instituicao) u).setRaioAtuacaoKm(15.0));

        Instant agora = Instant.now();
        lotes.cadastrar(PADARIA, new NovoLote("Pão francês", "Padaria", 50, "un", new BigDecimal("9.90"), null,
                agora.plus(Duration.ofHours(6)), "Manter em local seco; embalado em sacos de 10 unidades.",
                agora, agora.plus(Duration.ofHours(4)), null, null, null));
        lotes.cadastrar(PADARIA, new NovoLote("Bolo de cenoura", "Confeitaria", 8, "un", null, null,
                agora.plus(Duration.ofHours(30)), "Refrigerado.", agora, agora.plus(Duration.ofHours(24)),
                null, null, null));
        log.info("Dados de demonstração criados. Documentos: admin {}, doador {}, receptor aprovado {}, em análise {}",
                ADMIN, PADARIA, ONG, ABRIGO);
    }
}
