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

    /** Até que distância (km) a instituição consegue buscar lotes (UC04-2a). Nulo = sem limite próprio. */
    private Double raioAtuacaoKm;

    protected Instituicao() {}

    public Instituicao(String documento, String nome, String email) {
        super(documento, nome, email);
    }

    public StatusAnalise getStatusAnalise() { return statusAnalise; }
    public boolean isAprovada() { return statusAnalise == StatusAnalise.APROVADA; }
    public void aprovar() { this.statusAnalise = StatusAnalise.APROVADA; }
    public Double getRaioAtuacaoKm() { return raioAtuacaoKm; }
    public void setRaioAtuacaoKm(Double raioAtuacaoKm) { this.raioAtuacaoKm = raioAtuacaoKm; }

    @Override
    public Perfil getPerfil() { return Perfil.RECEPTOR; }
}
