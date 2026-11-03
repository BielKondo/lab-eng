package br.mackenzie.resgatai.comum;

/**
 * Ator secundário "Serviço de E-mail / Sistema de Notificação" (UC01 passo 8, UC03, UC04 passo 11, RF07):
 * entrega uma mensagem ao usuário. Deve lançar exceção quando o envio falhar.
 */
public interface Notificador {
    void enviar(String email, String titulo, String mensagem);
}
