package br.mackenzie.resgatai.api;

import br.mackenzie.resgatai.comum.NaoEncontradoException;

import br.mackenzie.resgatai.notificacao.NotificacaoService;
import br.mackenzie.resgatai.usuario.Usuario;
import br.mackenzie.resgatai.usuario.UsuarioService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** UC03 (consulta das notificações enviadas) e UC04, passo 1. */
@RestController
@RequestMapping("/api/notificacoes")
public class NotificacaoController {

    private final NotificacaoService notificacoes;
    private final UsuarioService usuarios;

    public NotificacaoController(NotificacaoService notificacoes, UsuarioService usuarios) {
        this.notificacoes = notificacoes;
        this.usuarios = usuarios;
    }

    @GetMapping
    public List<Dto.NotificacaoDto> listar(@RequestHeader(UsuarioController.CABECALHO_DOCUMENTO) String documento) {
        return notificacoes.listar(usuario(documento)).stream().map(Dto.NotificacaoDto::de).toList();
    }

    @PostMapping("/{id}/lida")
    public void marcarComoLida(@RequestHeader(UsuarioController.CABECALHO_DOCUMENTO) String documento,
                               @PathVariable("id") Long id) {
        notificacoes.marcarComoLida(usuario(documento), id);
    }

    private Usuario usuario(String documento) {
        return usuarios.buscarPorDocumento(documento).filter(Usuario::isAtivo)
                .orElseThrow(() -> new NaoEncontradoException("Usuário não encontrado: " + documento));
    }
}
