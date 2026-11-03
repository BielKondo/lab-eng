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
import java.time.Duration;
import java.time.Instant;

/** Lote de alimentos publicado por um Doador (UC02). Segue a máquina de estados de {@link StatusLote}. */
@Entity
public class LoteAlimento {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(optional = false)
    private Estabelecimento estabelecimento;

    @Column(nullable = false)
    private String nome; // tipo de alimento

    private String categoria;
    private int quantidade;
    private String unidadeMedida;
    private BigDecimal precoReduzido;
    @Column(length = 500)
    private String foto; // URL da imagem

    @Column(nullable = false)
    private Instant dataValidade;

    @Column(length = 500)
    private String condicoesConservacao;

    // Janela de retirada
    @Column(nullable = false)
    private Instant inicioRetirada;

    @Column(nullable = false)
    private Instant fimRetirada;

    // Endereço de retirada: o do Doador ou o alternativo informado para este lote (UC02-7a)
    @Column(nullable = false)
    private String enderecoRetirada;
    private Double latitude;
    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusLote status = StatusLote.DISPONIVEL;

    @Column(nullable = false)
    private Instant dataCadastro = Instant.now();

    protected LoteAlimento() {} // exigido pelo JPA

    public LoteAlimento(Estabelecimento estabelecimento, NovoLote dados,
                        String enderecoRetirada, Double latitude, Double longitude) {
        this.estabelecimento = estabelecimento;
        this.nome = dados.nome().trim();
        this.categoria = dados.categoria() == null || dados.categoria().isBlank() ? null : dados.categoria().trim();
        this.quantidade = dados.quantidade();
        this.unidadeMedida = dados.unidadeMedida().trim();
        this.precoReduzido = dados.precoReduzido();
        this.foto = dados.foto();
        this.dataValidade = dados.dataValidade();
        this.condicoesConservacao = dados.condicoesConservacao();
        this.inicioRetirada = dados.inicioRetirada();
        this.fimRetirada = dados.fimRetirada();
        this.enderecoRetirada = enderecoRetirada;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void alterarStatus(StatusLote novoStatus) {
        if (!status.podeIrPara(novoStatus)) {
            throw new IllegalStateException("Transição inválida: " + status + " -> " + novoStatus);
        }
        this.status = novoStatus;
    }

    /** Tempo até o vencimento (negativo se já venceu). */
    public Duration prazoRestante(Instant agora) {
        return Duration.between(agora, dataValidade);
    }

    public boolean vencido(Instant agora) {
        return !dataValidade.isAfter(agora);
    }

    /** A janela de retirada já se encerrou? */
    public boolean janelaEncerrada(Instant agora) {
        return !fimRetirada.isAfter(agora);
    }

    /** UC04, pré-condição: DISPONIVEL, não vencido e com a janela de retirada ainda aberta. */
    public boolean aptoParaReserva(Instant agora) {
        return status == StatusLote.DISPONIVEL && !vencido(agora) && !janelaEncerrada(agora);
    }

    public boolean possuiLocalizacao() {
        return latitude != null && longitude != null;
    }

    public boolean isDoacao() {
        return precoReduzido == null || precoReduzido.signum() == 0;
    }

    public Long getId() { return id; }
    public Estabelecimento getEstabelecimento() { return estabelecimento; }
    public String getNome() { return nome; }
    public String getCategoria() { return categoria; }
    public int getQuantidade() { return quantidade; }
    public String getUnidadeMedida() { return unidadeMedida; }
    public BigDecimal getPrecoReduzido() { return precoReduzido; }
    public String getFoto() { return foto; }
    public Instant getDataValidade() { return dataValidade; }
    public String getCondicoesConservacao() { return condicoesConservacao; }
    public Instant getInicioRetirada() { return inicioRetirada; }
    public Instant getFimRetirada() { return fimRetirada; }
    public String getEnderecoRetirada() { return enderecoRetirada; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public StatusLote getStatus() { return status; }
    public Instant getDataCadastro() { return dataCadastro; }
}
