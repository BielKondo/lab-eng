package br.mackenzie.resgatai.usuario;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validação de formato do UC01 (passo 6 e fluxo 3b, passo 4).
 *
 * <ul>
 *   <li>Tipo de perfil, CNPJ/CPF (com dígitos verificadores), nome (3 a 150 caracteres) e e-mail: obrigatórios.</li>
 *   <li>Doador e Receptor: telefone (DDD + número) e endereço também obrigatórios — a coleta depende deles.</li>
 *   <li>Receptor com CNPJ: precisa estar regular na Base de Validação de Instituições.</li>
 * </ul>
 */
public final class ValidadorUsuario {

    private static final String OBRIGATORIO = "Campo obrigatório não preenchido.";
    private static final Pattern EMAIL =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");

    private ValidadorUsuario() {}

    /** @return campo → mensagem de cada inconsistência; vazio se os dados forem válidos */
    public static Map<String, String> validar(DadosUsuario d, BaseValidacaoInstituicoes base) {
        Map<String, String> erros = new LinkedHashMap<>();
        if (d == null) {
            erros.put("formulario", "Nenhum dado foi informado.");
            return erros;
        }
        if (d.perfil() == null) {
            erros.put("perfil", "Informe o tipo de perfil (Doador, Receptor ou Administrador).");
        }

        if (vazio(d.documento())) {
            erros.put("documento", OBRIGATORIO);
        } else if (!documentoValido(d.documento())) {
            erros.put("documento", "CNPJ/CPF inválido (verifique os dígitos).");
        } else if (d.perfil() == Perfil.RECEPTOR && somenteDigitos(d.documento()).length() == 14
                && !base.instituicaoRegular(somenteDigitos(d.documento()))) {
            erros.put("documento", "Instituição não localizada ou irregular na Base de Validação de Instituições.");
        }

        if (vazio(d.nome())) {
            erros.put("nome", OBRIGATORIO);
        } else if (d.nome().trim().length() < 3 || d.nome().trim().length() > 150) {
            erros.put("nome", "O nome deve ter entre 3 e 150 caracteres.");
        }

        if (vazio(d.email())) {
            erros.put("email", OBRIGATORIO);
        } else if (!EMAIL.matcher(d.email().trim()).matches()) {
            erros.put("email", "E-mail fora do formato esperado (ex.: nome@dominio.com).");
        }

        boolean exigeContato = d.perfil() == Perfil.DOADOR || d.perfil() == Perfil.RECEPTOR;
        if (vazio(d.telefone())) {
            if (exigeContato) erros.put("telefone", "Campo obrigatório para Doadores e Receptores.");
        } else if (d.telefone().length() > 30 || !telefoneValido(d.telefone())) {
            erros.put("telefone", "Telefone deve ter DDD + número (10 ou 11 dígitos).");
        }
        if (vazio(d.endereco())) {
            if (exigeContato) erros.put("endereco", "Campo obrigatório para Doadores e Receptores.");
        } else if (d.endereco().trim().length() < 5) {
            erros.put("endereco", "Endereço incompleto.");
        } else if (d.endereco().trim().length() > 255) {
            erros.put("endereco", "No máximo 255 caracteres.");
        }
        if ((d.latitude() == null) != (d.longitude() == null)) {
            erros.put("localizacao", "Informe latitude e longitude juntas.");
        } else if (d.latitude() != null
                && (Math.abs(d.latitude()) > 90 || Math.abs(d.longitude()) > 180)) {
            erros.put("localizacao", "Coordenadas fora da faixa válida.");
        }
        return erros;
    }

    private static boolean telefoneValido(String telefone) {
        if (!telefone.trim().matches("[0-9()+\\-\\s]*")) {
            return false;
        }
        String digitos = telefone.replaceAll("\\D", "");
        if (digitos.startsWith("55") && digitos.length() > 11) {
            digitos = digitos.substring(2); // aceita o código do país
        }
        return digitos.length() == 10 || digitos.length() == 11;
    }

    // ---------- CPF / CNPJ ----------

    /** Remove a pontuação; devolve "" se houver caracteres que não sejam dígitos, ".", "/", "-" ou espaço. */
    public static String somenteDigitos(String documento) {
        if (documento == null || !documento.trim().matches("[0-9./\\-\\s]*")) {
            return "";
        }
        return documento.replaceAll("\\D", "");
    }

    /** CPF (11 dígitos) ou CNPJ (14 dígitos) com dígitos verificadores corretos. */
    public static boolean documentoValido(String documento) {
        String d = somenteDigitos(documento);
        if ((d.length() != 11 && d.length() != 14) || d.chars().allMatch(c -> c == d.charAt(0))) {
            return false;
        }
        int[] pesos1 = d.length() == 11
                ? new int[] {10, 9, 8, 7, 6, 5, 4, 3, 2}
                : new int[] {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] pesos2 = d.length() == 11
                ? new int[] {11, 10, 9, 8, 7, 6, 5, 4, 3, 2}
                : new int[] {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        return digito(d, pesos1) == d.charAt(pesos1.length) - '0'
                && digito(d, pesos2) == d.charAt(pesos2.length) - '0';
    }

    private static int digito(String d, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += (d.charAt(i) - '0') * pesos[i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    /** Padroniza em 000.000.000-00 (CPF) ou 00.000.000/0000-00 (CNPJ); outros valores voltam como vieram. */
    public static String formatarDocumento(String documento) {
        String d = somenteDigitos(documento);
        if (d.length() == 11) {
            return d.replaceFirst("(\\d{3})(\\d{3})(\\d{3})(\\d{2})", "$1.$2.$3-$4");
        }
        if (d.length() == 14) {
            return d.replaceFirst("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})", "$1.$2.$3/$4-$5");
        }
        return documento;
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }
}
