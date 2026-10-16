package br.mackenzie.resgatai.estoque;

import java.math.BigDecimal;
import java.time.Instant;

/** Dados que o Doador informa ao cadastrar um lote (UC02). Preço e foto são opcionais. */
public record NovoLote(String nome, // tipo de alimento, ex.: "Pão francês"
                       String categoria,
                       int quantidade,
                       BigDecimal precoReduzido,
                       String foto,
                       Instant dataValidade,
                       Instant inicioRetirada,
                       Instant fimRetirada) {}
