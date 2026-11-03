package br.mackenzie.resgatai.comum;

import br.mackenzie.resgatai.usuario.BaseValidacaoInstituicoes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Implementações provisórias dos atores externos, enquanto não há integração real.
 * Para integrar (servidor de e-mail, serviço de push, base oficial de CNPJs), basta substituir estes beans.
 */
@Configuration
public class IntegracoesExternas {

    private static final Logger log = LoggerFactory.getLogger(IntegracoesExternas.class);

    /** Ainda sem e-mail/push reais: registra o envio no log. As notificações também ficam gravadas no banco. */
    @Bean
    public Notificador notificador() {
        return (email, titulo, mensagem) -> log.info("Mensagem para {}: {} — {}", email, titulo, mensagem);
    }

    /** Ainda sem base externa: considera toda instituição regular. */
    @Bean
    public BaseValidacaoInstituicoes baseValidacaoInstituicoes() {
        return cnpj -> true;
    }
}
