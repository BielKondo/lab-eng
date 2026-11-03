package br.mackenzie.resgatai.comum;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Log de auditoria do sistema: quem, quando, qual operação, sobre qual usuário e quais campos mudaram.
 * Nas operações de lote/reserva, {@code usuarioAlvoId} é o Doador ou Receptor que executou a operação
 * e a descrição identifica o lote.
 */
@Entity
public class RegistroAuditoria {

    public enum Operacao {
        CADASTRO_ADMINISTRADOR_INICIAL, CADASTRO, ATUALIZACAO, INATIVACAO, // UC01
        CADASTRO_LOTE,                                                     // UC02
        RESERVA_LOTE, LOTE_EXPIRADO                                        // UC04
    }

    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false)
    private Instant dataHora;

    @Column(nullable = false)
    private String autor; // documento de quem executou

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Operacao operacao;

    @Column(nullable = false)
    private Long usuarioAlvoId;

    private String camposModificados; // ex.: "nome, email"

    @Column(length = 1000)
    private String descricao;

    protected RegistroAuditoria() {} // exigido pelo JPA

    public RegistroAuditoria(String autor, Operacao operacao, Long usuarioAlvoId,
                             List<String> camposModificados, String descricao) {
        this.dataHora = Instant.now();
        this.autor = autor;
        this.operacao = operacao;
        this.usuarioAlvoId = usuarioAlvoId;
        this.camposModificados = String.join(", ", camposModificados);
        this.descricao = descricao;
    }

    public Long getId() { return id; }
    public Instant getDataHora() { return dataHora; }
    public String getAutor() { return autor; }
    public Operacao getOperacao() { return operacao; }
    public Long getUsuarioAlvoId() { return usuarioAlvoId; }
    public String getDescricao() { return descricao; }

    public List<String> getCamposModificados() {
        return camposModificados == null || camposModificados.isBlank()
                ? List.of() : Arrays.asList(camposModificados.split(", "));
    }
}
