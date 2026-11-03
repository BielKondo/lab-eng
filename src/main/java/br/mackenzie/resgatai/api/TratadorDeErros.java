package br.mackenzie.resgatai.api;

import br.mackenzie.resgatai.comum.AcessoNegadoException;
import br.mackenzie.resgatai.comum.DadosInvalidosException;
import br.mackenzie.resgatai.comum.NaoEncontradoException;
import br.mackenzie.resgatai.reserva.LoteExpiradoException;
import br.mackenzie.resgatai.reserva.LoteIndisponivelException;
import br.mackenzie.resgatai.usuario.UsuarioJaCadastradoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Converte as exceções dos fluxos alternativos/de exceção em respostas HTTP com corpo JSON:
 * 400 dados inválidos (UC01-6a, UC02-5a/7a/9a), 403 pré-condição de perfil, 404 não encontrado,
 * 409 conflito (UC01-6b, UC04-6a), 410 lote expirado (UC04-6b).
 */
@RestControllerAdvice
public class TratadorDeErros {

    @ExceptionHandler(DadosInvalidosException.class)
    public ResponseEntity<Dto.ErroDto> dadosInvalidos(DadosInvalidosException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new Dto.ErroDto("Dados obrigatórios ausentes ou inválidos.", e.getInconsistencias()));
    }

    @ExceptionHandler(UsuarioJaCadastradoException.class)
    public ResponseEntity<Dto.ErroDto> usuarioJaCadastrado(UsuarioJaCadastradoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new Dto.ErroDto(e.getMessage(), Dto.UsuarioDto.de(e.getUsuarioExistente())));
    }

    @ExceptionHandler(LoteIndisponivelException.class)
    public ResponseEntity<Dto.ErroDto> loteIndisponivel(LoteIndisponivelException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new Dto.ErroDto(e.getMessage(), e.getAlternativas().stream().map(Dto.LoteDto::de).toList()));
    }

    @ExceptionHandler(LoteExpiradoException.class)
    public ResponseEntity<Dto.ErroDto> loteExpirado(LoteExpiradoException e) {
        return ResponseEntity.status(HttpStatus.GONE).body(new Dto.ErroDto(e.getMessage(), null));
    }

    @ExceptionHandler(AcessoNegadoException.class)
    public ResponseEntity<Dto.ErroDto> acessoNegado(AcessoNegadoException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new Dto.ErroDto(e.getMessage(), null));
    }

    @ExceptionHandler(NaoEncontradoException.class)
    public ResponseEntity<Dto.ErroDto> naoEncontrado(NaoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new Dto.ErroDto(e.getMessage(), null));
    }

    /** Demais regras de negócio violadas (ex.: alterar CNPJ/CPF, perfil inicial errado). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Dto.ErroDto> argumentoInvalido(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new Dto.ErroDto(e.getMessage(), null));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Dto.ErroDto> estadoInvalido(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new Dto.ErroDto(e.getMessage(), null));
    }
}
