package br.mackenzie.resgatai.api;

import br.mackenzie.resgatai.comum.NaoEncontradoException;

import br.mackenzie.resgatai.usuario.Perfil;
import br.mackenzie.resgatai.usuario.UsuarioService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC01 — Gerenciar Usuários (CRUD) e RF06 — Aprovar Instituição.
 *
 * <p>Enquanto o caso de uso "Autenticar Usuário" não existe, quem chama se identifica pelo
 * cabeçalho {@code X-Documento} (CPF/CNPJ, com ou sem pontuação). A pré-condição de cada operação
 * continua sendo verificada pelo serviço. Nos caminhos ({@code /{documento}}) o documento vai
 * <b>só com dígitos</b>, porque a barra do CNPJ não pode aparecer na URL.</p>
 */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    public static final String CABECALHO_DOCUMENTO = "X-Documento";

    private final UsuarioService usuarios;

    public UsuarioController(UsuarioService usuarios) {
        this.usuarios = usuarios;
    }

    /** Identificação do usuário atual (usada pela tela "Entrar" do front-end). */
    @GetMapping("/eu")
    public Dto.UsuarioDto eu(@RequestHeader(CABECALHO_DOCUMENTO) String documento) {
        return usuarios.buscarPorDocumento(documento).map(Dto.UsuarioDto::de)
                .orElseThrow(() -> new NaoEncontradoException("Usuário não encontrado: " + documento));
    }

    @GetMapping
    public List<Dto.UsuarioDto> listar(@RequestHeader(CABECALHO_DOCUMENTO) String admin) {
        return usuarios.listar(admin).stream().map(Dto.UsuarioDto::de).toList();
    }

    @PostMapping
    public ResponseEntity<Dto.UsuarioDto> cadastrar(@RequestHeader(CABECALHO_DOCUMENTO) String admin,
                                                    @RequestBody Dto.UsuarioForm dados) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Dto.UsuarioDto.de(usuarios.cadastrar(admin, dados.paraDominio())));
    }

    @GetMapping("/busca")
    public List<Dto.UsuarioDto> consultar(@RequestHeader(CABECALHO_DOCUMENTO) String admin,
                                          @RequestParam(name = "nome", required = false) String nome,
                                          @RequestParam(name = "documento", required = false) String documento,
                                          @RequestParam(name = "perfil", required = false) Perfil perfil,
                                          @RequestParam(name = "ativo", required = false) Boolean ativo) {
        return usuarios.consultar(admin, nome, documento, perfil, ativo).stream().map(Dto.UsuarioDto::de).toList();
    }

    @GetMapping("/{documento}")
    public Dto.DetalhesUsuarioDto detalhar(@RequestHeader(CABECALHO_DOCUMENTO) String admin,
                                           @PathVariable("documento") String documento) {
        return Dto.DetalhesUsuarioDto.de(usuarios.detalhar(admin, documento));
    }

    @GetMapping("/{documento}/formulario")
    public Dto.UsuarioForm prepararAtualizacao(@RequestHeader(CABECALHO_DOCUMENTO) String admin,
                                               @PathVariable("documento") String documento) {
        return Dto.UsuarioForm.de(usuarios.prepararAtualizacao(admin, documento));
    }

    @PutMapping("/{documento}")
    public Dto.UsuarioDto atualizar(@RequestHeader(CABECALHO_DOCUMENTO) String admin,
                                    @PathVariable("documento") String documento, @RequestBody Dto.UsuarioForm dados) {
        return Dto.UsuarioDto.de(usuarios.atualizar(admin, documento, dados.paraDominio()));
    }

    /** 3c, passo 3: mensagem de confirmação (sem alterar nada). */
    @GetMapping("/{documento}/inativacao")
    public Map<String, String> prepararInativacao(@RequestHeader(CABECALHO_DOCUMENTO) String admin,
                                                  @PathVariable("documento") String documento) {
        return Map.of("confirmacao", usuarios.prepararInativacao(admin, documento));
    }

    /** 3c, passos 4-5: após a confirmação do administrador. */
    @DeleteMapping("/{documento}")
    public Dto.UsuarioDto inativar(@RequestHeader(CABECALHO_DOCUMENTO) String admin,
                                   @PathVariable("documento") String documento) {
        return Dto.UsuarioDto.de(usuarios.inativar(admin, documento));
    }

    /** RF06. */
    @PostMapping("/{documento}/aprovacao")
    public Map<String, Boolean> aprovar(@RequestHeader(CABECALHO_DOCUMENTO) String admin,
                                        @PathVariable("documento") String documento) {
        usuarios.listar(admin); // garante a pré-condição de administrador
        return Map.of("aprovada", usuarios.aprovarInstituicao(documento));
    }
}
