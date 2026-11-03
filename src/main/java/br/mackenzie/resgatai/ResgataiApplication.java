package br.mackenzie.resgatai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication
public class ResgataiApplication {

    private static final Logger log = LoggerFactory.getLogger(ResgataiApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(ResgataiApplication.class, args);
    }

    /** Mostra o endereço da aplicação no console quando ela termina de subir. */
    @EventListener(ApplicationReadyEvent.class)
    public void mostrarEndereco(ApplicationReadyEvent evento) {
        String porta = evento.getApplicationContext().getEnvironment().getProperty("local.server.port");
        if (porta != null) { // nos testes não há servidor web real
            log.info("ResgatAI no ar: http://localhost:{}  (console do banco: http://localhost:{}/h2-console)", porta, porta);
        }
    }
}
