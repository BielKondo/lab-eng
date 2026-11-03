package br.mackenzie.resgatai.usuario;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** UC01, passo 6: regras de formato (sem Spring, como o LoteAlimentoTest). */
public class ValidadorUsuarioTest {

    private static final BaseValidacaoInstituicoes ACEITA_TODAS = cnpj -> true;

    private static DadosUsuario doador(String documento, String email, String telefone) {
        return new DadosUsuario(Perfil.DOADOR, documento, "Padaria Teste", email, telefone, "Rua A, 10 - Sao Paulo/SP");
    }

    private static Set<String> erros(DadosUsuario d) {
        return ValidadorUsuario.validar(d, ACEITA_TODAS).keySet();
    }

    @Test
    public void testDocumentosValidos() {
        for (String doc : new String[] {"529.982.247-25", "52998224725", "123.456.789-09",
                "98.765.432/0001-98", "11222333000181"}) {
            assertTrue(ValidadorUsuario.documentoValido(doc), doc);
        }
    }

    @Test
    public void testDocumentosInvalidos() {
        for (String doc : new String[] {"529.982.247-26", "98.765.432/0001-10", "111.111.111-11",
                "00.000.000/0000-00", "1234567890", "123456789012345", "abc.def.ghi-jk", "", null}) {
            assertFalse(ValidadorUsuario.documentoValido(doc), String.valueOf(doc));
        }
    }

    @Test
    public void testFormataDocumento() {
        assertEquals("529.982.247-25", ValidadorUsuario.formatarDocumento("52998224725"));
        assertEquals("98.765.432/0001-98", ValidadorUsuario.formatarDocumento("98765432000198"));
    }

    @Test
    public void testEmail() {
        assertTrue(erros(doador("98.765.432/0001-98", "nome@dominio.com.br", "11 3333-4444")).isEmpty());
        for (String email : new String[] {"sem-arroba", "a@b", "a@b.", "@dominio.com", "nome @dominio.com"}) {
            assertEquals(Set.of("email"), erros(doador("98.765.432/0001-98", email, "11 3333-4444")), email);
        }
    }

    @Test
    public void testTelefone() {
        for (String tel : new String[] {"1133334444", "(11) 98888-7777", "+55 11 98888-7777"}) {
            assertTrue(erros(doador("98.765.432/0001-98", "a@b.com", tel)).isEmpty(), tel);
        }
        for (String tel : new String[] {"3333-4444", "11 3333-444a", "123"}) {
            assertEquals(Set.of("telefone"), erros(doador("98.765.432/0001-98", "a@b.com", tel)), tel);
        }
    }

    @Test
    public void testNomeCurtoEFormularioNulo() {
        DadosUsuario d = new DadosUsuario(Perfil.ADMINISTRADOR, "529.982.247-25", "ab", "a@b.com", null, null);
        assertEquals(Set.of("nome"), erros(d));
        assertEquals(Set.of("formulario"), erros(null));
    }

    @Test
    public void testReceptorComCpfNaoConsultaABase() {
        DadosUsuario d = new DadosUsuario(Perfil.RECEPTOR, "123.456.789-09", "Cozinha Comunitaria", "c@c.org",
                "11 4002-8922", "Rua B, 20 - Sao Paulo/SP");
        assertTrue(ValidadorUsuario.validar(d, cnpj -> false).isEmpty());
    }
}
