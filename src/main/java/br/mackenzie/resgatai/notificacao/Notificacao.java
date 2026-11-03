package br.mackenzie.resgatai.notificacao;

import br.mackenzie.resgatai.estoque.LoteAlimento;
import br.mackenzie.resgatai.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.time.Instant;

/**
 * Registro de uma notificação enviada (ou cuja tentativa falhou). É o que o Receptor
 * "visualiza" no passo 1 do UC04 e o que o Doador recebe no passo 11.
 */
@Entity
public class Notificacao {

    public enum Tipo { NOVO_LOTE_PROXIMO, RESERVA_RECEBIDA, LOTE_EXPIRADO }

    public enum Situacao { ENVIADA, FALHA }

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(optional = false)
    private Usuario destinatario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tipo tipo;

    @ManyToOne
    private LoteAlimento lote;

    @Column(nullable = false)
    private String titulo;

    @Column(nullable = false, length = 1000)
    private String mensagem;

    private Double distanciaKm; // distância do receptor até o ponto de retirada (UC03)

    @Column(nullable = false)
    private Instant dataHora = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Situacao situacao = Situacao.ENVIADA;

    private String motivoFalha;
    private boolean lida;

    protected Notificacao() {} // exigido pelo JPA

    public Notificacao(Usuario destinatario, Tipo tipo, LoteAlimento lote, String titulo, String mensagem,
                       Double distanciaKm) {
        this.destinatario = destinatario;
        this.tipo = tipo;
        this.lote = lote;
        this.titulo = titulo;
        this.mensagem = mensagem;
        this.distanciaKm = distanciaKm;
    }

    void registrarFalha(String motivo) {
        this.situacao = Situacao.FALHA;
        this.motivoFalha = motivo;
    }

    public void marcarComoLida() { this.lida = true; }

    public Long getId() { return id; }
    public Usuario getDestinatario() { return destinatario; }
    public Tipo getTipo() { return tipo; }
    public LoteAlimento getLote() { return lote; }
    public String getTitulo() { return titulo; }
    public String getMensagem() { return mensagem; }
    public Double getDistanciaKm() { return distanciaKm; }
    public Instant getDataHora() { return dataHora; }
    public Situacao getSituacao() { return situacao; }
    public String getMotivoFalha() { return motivoFalha; }
    public boolean isLida() { return lida; }
}
