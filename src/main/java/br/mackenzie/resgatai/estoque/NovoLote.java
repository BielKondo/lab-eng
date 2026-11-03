package br.mackenzie.resgatai.estoque;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Formulário de cadastro de lote (UC02, passo 2): tipo de alimento, categoria, quantidade,
 * unidade de medida, data de validade, condições de conservação e janela de retirada.
 * Preço reduzido e foto vêm do RF02 e são opcionais. O endereço de retirada só é informado
 * no fluxo alternativo 7a (endereço diferente do cadastro do Doador).
 */
public record NovoLote(String nome,                 // tipo de alimento, ex.: "Pão francês"
                       String categoria,
                       int quantidade,
                       String unidadeMedida,        // un, kg, L, cx...
                       BigDecimal precoReduzido,    // nulo ou zero = doação
                       String foto,                 // URL da imagem
                       Instant dataValidade,
                       String condicoesConservacao, // observações sobre conservação e embalagem
                       Instant inicioRetirada,
                       Instant fimRetirada,
                       String enderecoRetirada,     // 7a: opcional; nulo = endereço do Doador
                       Double latitude,             // coordenadas do endereço alternativo (opcionais)
                       Double longitude) {

    /** Lote sem preço, foto, observações e endereço alternativo. */
    public NovoLote(String nome, String categoria, int quantidade, String unidadeMedida,
                    Instant dataValidade, Instant inicioRetirada, Instant fimRetirada) {
        this(nome, categoria, quantidade, unidadeMedida, null, null, dataValidade, null,
                inicioRetirada, fimRetirada, null, null, null);
    }

    public NovoLote comQuantidadeEValidade(int quantidade, Instant dataValidade) {
        return new NovoLote(nome, categoria, quantidade, unidadeMedida, precoReduzido, foto, dataValidade,
                condicoesConservacao, inicioRetirada, fimRetirada, enderecoRetirada, latitude, longitude);
    }

    public NovoLote comEnderecoRetirada(String endereco, Double latitude, Double longitude) {
        return new NovoLote(nome, categoria, quantidade, unidadeMedida, precoReduzido, foto, dataValidade,
                condicoesConservacao, inicioRetirada, fimRetirada, endereco, latitude, longitude);
    }

    public NovoLote comPrecoEFoto(BigDecimal preco, String foto) {
        return new NovoLote(nome, categoria, quantidade, unidadeMedida, preco, foto, dataValidade,
                condicoesConservacao, inicioRetirada, fimRetirada, enderecoRetirada, latitude, longitude);
    }
}
