package br.mackenzie.resgatai.usuario;

/** Ator secundário do UC01: confirma se a instituição (Receptor com CNPJ) está regular. */
public interface BaseValidacaoInstituicoes {
    boolean instituicaoRegular(String cnpjSomenteDigitos);
}
