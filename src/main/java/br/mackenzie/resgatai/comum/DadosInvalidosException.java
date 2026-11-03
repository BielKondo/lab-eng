package br.mackenzie.resgatai.comum;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dados obrigatórios ausentes ou inválidos (UC01-6a, UC02-5a/7a/9a).
 * Traz cada inconsistência (campo → mensagem). Nada é gravado, e o formulário enviado
 * continua com quem chamou, para corrigir só os campos apontados e confirmar de novo.
 */
public class DadosInvalidosException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final transient Map<String, String> inconsistencias;

    public DadosInvalidosException(Map<String, String> inconsistencias) {
        super("Dados obrigatórios ausentes ou inválidos: " + inconsistencias);
        this.inconsistencias = new LinkedHashMap<>(inconsistencias);
    }

    public Map<String, String> getInconsistencias() {
        return inconsistencias;
    }
}
