package br.mackenzie.resgatai.estoque;

import br.mackenzie.resgatai.usuario.Estabelecimento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
public class LoteAlimento {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(optional = false)
    private Estabelecimento estabelecimento;

    @Column(nullable = false)
    private String nome;

    private String categoria;
    private int quantidade;
    private BigDecimal precoReduzido;
    private String foto; // URL da imagem

    @Column(nullable = false)
    private Instant dataValidade;

    // Janela de retirada
    @Column(nullable = false)
    private Instant inicioRetirada;

    @Column(nullable = false)
    private Instant fimRetirada;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusLote status = StatusLote.DISPONIVEL;

    protected LoteAlimento() {} // exigido pelo JPA

    public LoteAlimento(Estabelecimento estabelecimento, NovoLote dados) {
        this.estabelecimento = estabelecimento;
        this.nome = dados.nome();
        this.categoria = dados.categoria();
        this.quantidade = dados.quantidade();
        this.precoReduzido = dados.precoReduzido();
        this.foto = dados.foto();
        this.dataValidade = dados.dataValidade();
        this.inicioRetirada = dados.inicioRetirada();
        this.fimRetirada = dados.fimRetirada();
    }

    public void alterarStatus(StatusLote novoStatus) {
        if (!status.podeIrPara(novoStatus)) {
            throw new IllegalStateException("Transição inválida: " + status + " -> " + novoStatus);
        }
        this.status = novoStatus;
    }

    public Long getId() { return id; }
    public Estabelecimento getEstabelecimento() { return estabelecimento; }
    public String getNome() { return nome; }
    public String getCategoria() { return categoria; }
    public int getQuantidade() { return quantidade; }
    public BigDecimal getPrecoReduzido() { return precoReduzido; }
    public String getFoto() { return foto; }
    public Instant getDataValidade() { return dataValidade; }
    public Instant getInicioRetirada() { return inicioRetirada; }
    public Instant getFimRetirada() { return fimRetirada; }
    public StatusLote getStatus() { return status; }
}
