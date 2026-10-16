package br.mackenzie.resgatai.usuario;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/** Doador: mercado, padaria, distribuidor. */
@Entity
@DiscriminatorValue("ESTABELECIMENTO")
public class Estabelecimento extends Usuario {

    protected Estabelecimento() {}

    public Estabelecimento(String documento, String nome, String email) {
        super(documento, nome, email);
    }
}
