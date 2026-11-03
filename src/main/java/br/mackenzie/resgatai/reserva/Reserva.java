package br.mackenzie.resgatai.reserva;

import br.mackenzie.resgatai.estoque.LoteAlimento;
import br.mackenzie.resgatai.usuario.Instituicao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * Vínculo exclusivo entre um Receptor e um lote (UC04), com o PIN/QR Code usado na retirada
 * presencial (UC06) e o prazo limite de retirada (UC05).
 */
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

    /** PIN de 6 dígitos para digitação manual (UC06-4a). */
    @Column(length = 6)
    private String pin;

    /** Conteúdo do QR Code; único por reserva e invalidado após a retirada (UC06). */
    @Column(unique = true)
    private String codigoValidacao;

    @Column(nullable = false)
    private Instant prazoLimiteRetirada;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusReserva status = StatusReserva.ATIVA;

    protected Reserva() {} // exigido pelo JPA

    public Reserva(LoteAlimento lote, Instituicao instituicao, Instant prazoLimiteRetirada) {
        this.lote = lote;
        this.instituicao = instituicao;
        this.dataHora = Instant.now();
        this.prazoLimiteRetirada = prazoLimiteRetirada;
    }

    /** UC04, passo 8: gera o PIN e o conteúdo do QR Code de validação. */
    public void gerarCodigo(SecureRandom aleatorio) {
        this.pin = String.format("%06d", aleatorio.nextInt(1_000_000));
        StringBuilder codigo = new StringBuilder("RESGATAI-");
        String alfabeto = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // sem caracteres ambíguos
        for (int i = 0; i < 16; i++) {
            codigo.append(alfabeto.charAt(aleatorio.nextInt(alfabeto.length())));
        }
        this.codigoValidacao = codigo.toString();
    }

    /** Texto codificado no QR Code apresentado ao Doador. */
    public String conteudoQrCode() {
        return "resgatai://retirada?codigo=" + codigoValidacao;
    }

    public boolean prazoExpirado(Instant agora) {
        return !prazoLimiteRetirada.isAfter(agora);
    }

    /** UC05: a reserva venceu sem retirada. */
    public void expirarTimeout() {
        this.status = StatusReserva.EXPIRADA;
    }

    public Long getId() { return id; }
    public LoteAlimento getLote() { return lote; }
    public Instituicao getInstituicao() { return instituicao; }
    public Instant getDataHora() { return dataHora; }
    public String getPin() { return pin; }
    public String getCodigoValidacao() { return codigoValidacao; }
    public Instant getPrazoLimiteRetirada() { return prazoLimiteRetirada; }
    public StatusReserva getStatus() { return status; }
}
