package br.mackenzie.resgatai.api;

import br.mackenzie.resgatai.estoque.LoteAlimentoService;
import br.mackenzie.resgatai.estoque.Urgencia;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC02 — Cadastrar Lote de Alimentos; busca de lotes disponíveis (RF03 / UC04-2a). */
@RestController
@RequestMapping("/api/lotes")
public class LoteController {

    private final LoteAlimentoService lotes;

    public LoteController(LoteAlimentoService lotes) {
        this.lotes = lotes;
    }

    /** UC02, passo 6: indicador de urgência calculado enquanto o Doador preenche a validade. */
    @GetMapping("/urgencia")
    public Map<String, String> urgencia(@RequestParam("dataValidade") Instant dataValidade) {
        Urgencia u = lotes.indicadorUrgencia(dataValidade);
        return Map.of("urgencia", u.name(), "descricao", u.getDescricao());
    }

    /** UC02, passos 8-12. */
    @PostMapping
    public ResponseEntity<Dto.ResumoLoteDto> cadastrar(@RequestHeader(UsuarioController.CABECALHO_DOCUMENTO) String doador,
                                                       @RequestBody Dto.LoteForm dados) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Dto.ResumoLoteDto.de(lotes.cadastrar(doador, dados.paraDominio())));
    }

    /** UC02-3a, passo 2. */
    @GetMapping("/meus")
    public List<Dto.LoteDto> meus(@RequestHeader(UsuarioController.CABECALHO_DOCUMENTO) String doador) {
        return lotes.listarDoDoador(doador).stream().map(l -> Dto.LoteDto.de(l, null)).toList();
    }

    /** UC02-3a, passo 4: formulário pré-preenchido (sem quantidade e validade). */
    @GetMapping("/{id}/repetir")
    public Dto.LoteForm repetir(@RequestHeader(UsuarioController.CABECALHO_DOCUMENTO) String doador, @PathVariable("id") Long id) {
        return Dto.LoteForm.de(lotes.prepararRepeticao(doador, id));
    }

    /** UC04-2a / RF03. */
    @GetMapping("/disponiveis")
    public List<Dto.LoteDto> disponiveis(@RequestHeader(UsuarioController.CABECALHO_DOCUMENTO) String receptor,
                                         @RequestParam(name = "categoria", required = false) String categoria,
                                         @RequestParam(name = "precoMaximo", required = false) BigDecimal precoMaximo,
                                         @RequestParam(name = "validadeAte", required = false) Instant validadeAte,
                                         @RequestParam(name = "raioKm", required = false) Double raioKm) {
        return lotes.buscarDisponiveis(receptor, categoria, precoMaximo, validadeAte, raioKm)
                .stream().map(Dto.LoteDto::de).toList();
    }

    /** UC04, passo 3: detalhes do lote. */
    @GetMapping("/{id}")
    public Dto.LoteDto detalhar(@PathVariable("id") Long id) {
        return Dto.LoteDto.de(lotes.detalhar(id), null);
    }
}
