package br.mackenzie.resgatai.usuario;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import java.util.HashSet;
import java.util.Set;

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "perfil")
public abstract class Usuario {

    /** Score de reputação atribuído no cadastro (UC01, passo 7). */
    public static final int SCORE_REPUTACAO_INICIAL = 100;

    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false, unique = true)
    private String documento; // CPF ou CNPJ

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true)
    private String email;

    private String endereco;
    private String telefone;

    // Localização do endereço (usada pelo Motor de Roteamento, UC03/UC04). Opcional até haver geocodificação.
    private Double latitude;
    private Double longitude;

    private boolean ativo = true; // Por padrão o usuário é criado ativo

    private int scoreReputacao = SCORE_REPUTACAO_INICIAL;

    // Tabela de segurança: permissões de acesso vinculadas ao perfil (UC01, passo 7)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "usuario_permissao")
    @Enumerated(EnumType.STRING)
    @Column(name = "permissao")
    private Set<Perfil.Permissao> permissoes = new HashSet<>();

    private String tokenPrimeiroAcesso; // link de definição de senha (UC01, passo 8)

    protected Usuario() {} // exigido pelo JPA

    protected Usuario(String documento, String nome, String email) {
        this.documento = documento;
        this.nome = nome;
        this.email = email;
    }

    /** Doador, Receptor ou Administrador — definido pela subclasse. */
    public abstract Perfil getPerfil();

    public Long getId() { return id; }
    public String getDocumento() { return documento; }
    void setDocumento(String documento) { this.documento = documento; } // usado só para padronizar a formatação
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getEndereco() { return endereco; }
    public void setEndereco(String endereco) { this.endereco = endereco; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public void setLocalizacao(Double latitude, Double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }
    /** Verdadeiro quando o endereço tem coordenadas e pode entrar no cálculo de distância. */
    public boolean possuiLocalizacao() { return latitude != null && longitude != null; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public int getScoreReputacao() { return scoreReputacao; }

    public Set<Perfil.Permissao> getPermissoes() { return Set.copyOf(permissoes); }

    void vincularPermissoes(Set<Perfil.Permissao> novas) {
        permissoes.clear();
        permissoes.addAll(novas);
    }

    void revogarPermissoes() {
        permissoes.clear();
        tokenPrimeiroAcesso = null;
    }

    public String getTokenPrimeiroAcesso() { return tokenPrimeiroAcesso; }
    void setTokenPrimeiroAcesso(String token) { this.tokenPrimeiroAcesso = token; }
}
