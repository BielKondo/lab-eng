package br.mackenzie.resgatai.api;

import br.mackenzie.resgatai.reserva.ReservaService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** UC04 — Reservar Lote de Alimentos. */
@RestController
@RequestMapping("/api/reservas")
public class ReservaController {

    public record PedidoReserva(Long loteId) {}

    private final ReservaService reservas;

    public ReservaController(ReservaService reservas) {
        this.reservas = reservas;
    }

    /** Passos 4-11. Respostas: 201 reserva com PIN/QR; 409 lote já reservado (6a); 410 lote expirado (6b). */
    @PostMapping
    public ResponseEntity<Dto.ReservaDto> reservar(@RequestHeader(UsuarioController.CABECALHO_DOCUMENTO) String receptor,
                                                   @RequestBody PedidoReserva pedido) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Dto.ReservaDto.de(reservas.reservar(receptor, pedido.loteId())));
    }

    @GetMapping
    public List<Dto.ReservaDto> minhas(@RequestHeader(UsuarioController.CABECALHO_DOCUMENTO) String receptor) {
        return reservas.minhasReservas(receptor).stream().map(Dto.ReservaDto::de).toList();
    }

    @GetMapping("/{id}")
    public Dto.ReservaDto detalhar(@RequestHeader(UsuarioController.CABECALHO_DOCUMENTO) String receptor,
                                   @PathVariable("id") Long id) {
        return Dto.ReservaDto.de(reservas.detalhar(receptor, id));
    }
}
