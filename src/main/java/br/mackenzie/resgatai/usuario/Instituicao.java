package br.mackenzie.resgatai.usuario;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** Receptor: ONG, igreja, abrigo, cozinha solidária. Só tem acesso total após aprovação do Administrador (RF06). */
@Entity
@DiscriminatorValue("INSTITUICAO")
public class Instituicao extends Usuario {

    public enum StatusAnalise { EM_ANALISE, APROVADA }

    @Enumerated(EnumType.STRING)
    private StatusAnalise statusAnalise = StatusAnalise.EM_ANALISE;

    protected Instituicao() {}

    public Instituicao(String documento, String nome, String email) {
        super(documento, nome, email);
    }

    public StatusAnalise getStatusAnalise() { return statusAnalise; }
    void aprovar() { this.statusAnalise = StatusAnalise.APROVADA; }
}
