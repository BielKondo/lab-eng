package br.mackenzie.resgatai.reserva;

import br.mackenzie.resgatai.estoque.LoteAlimento;
import br.mackenzie.resgatai.usuario.Instituicao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.time.Instant;

@Entity
public class Reserva {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(optional = false)
    private LoteAlimento lote; // um lote pode ter várias reservas ao longo do tempo (UC05 o devolve a DISPONIVEL)

    @ManyToOne(optional = false)
    private Instituicao instituicao;

    @Column(nullable = false)
    private Instant dataHora;

    private String codigoValidacao; // PIN/QR Code (UC04/UC06)

    protected Reserva() {} // exigido pelo JPA

    public Reserva(LoteAlimento lote, Instituicao instituicao) {
        this.lote = lote;
        this.instituicao = instituicao;
        this.dataHora = Instant.now();
    }

    public Long getId() { return id; }
    public LoteAlimento getLote() { return lote; }
    public Instituicao getInstituicao() { return instituicao; }
    public Instant getDataHora() { return dataHora; }
    public String getCodigoValidacao() { return codigoValidacao; }
}
