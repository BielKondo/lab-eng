import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class AdministradorTeste {

    private Administrador admin;
    private Usuario usuarioDoador;
    private Instituicao ongValida;

    @Before
    public void setUp() {
        admin = new Administrador();
        usuarioDoador = new Usuario("Supermercado Teste", "98.765.432/0001-10", "contato@teste.com", "Doador");
        ongValida = new Instituicao("ONG Teste", "12.345.678/0001-90");
    }

    @Test
    public void testGerenciarUsuariosCriarCamposObrigatorios() {
        System.out.println("- Tentando criar usuario com dados validos.");
        boolean criadoSucesso = admin.criarUsuario(usuarioDoador);
        System.out.println("  -> Validacao 1: Confirmando que o usuario foi cadastrado com sucesso.\n");
        assertTrue(criadoSucesso);

        System.out.println("- Tentando cadastrar um usuario nulo.");
        boolean criadoNulo = admin.criarUsuario(null);
        System.out.println("  -> Validacao 2: Confirmando que o sistema bloqueou o cadastro nulo.\n");
        assertFalse(criadoNulo);
    }

    @Test
    public void testGerenciarUsuariosCriarValidoEDuplicado() {
        System.out.println("- Cadastrando o primeiro usuário no sistema.");
        boolean primeiroCriado = admin.criarUsuario(usuarioDoador);
        System.out.println("  -> Validacao 1: Confirmando que o primeiro usuario foi cadastrado.\n");
        assertTrue(primeiroCriado);

        System.out.println("- Tentando cadastrar segundo usuario com o mesmo CNPJ/CPF.");
        Usuario usuarioDuplicado = new Usuario("Outro Supermercado", "98.765.432/0001-10", "outro@email.com", "Doador");
        boolean resultadoDuplicado = admin.criarUsuario(usuarioDuplicado);
        System.out.println("  -> Validacao 2: Verificando que o cadastro duplicado foi rejeitado.\n");
        assertFalse(resultadoDuplicado);
    }

    @Test
    public void testGerenciarUsuariosInativar() {
        System.out.println("- Cadastrando usuario inicial.");
        boolean criado = admin.criarUsuario(usuarioDoador);
        System.out.println("  -> Validacao 1: Confirmando que o usuario foi criado e esta ativo por padrao.\n");
        assertTrue(criado);
        assertTrue(usuarioDoador.isAtivo());

        System.out.println("- Executando comando de inativacao por CPF/CNPJ.");
        boolean inativado = admin.inativarUsuario(usuarioDoador.getCpfCnpj());
        System.out.println("  -> Validacao 2: Confirmando que o status do usuario mudou para inativo (false).\n");
        assertTrue(inativado);
        assertFalse(usuarioDoador.isAtivo());
    }

    @Test
    public void testAprovarInstituicaoSucessoEErro() {
        System.out.println("- Verificando se instituicao esta valida para aprovacao.");
        boolean aprovado = admin.aprovarInstituicao(ongValida);
        System.out.println("  -> Validacao 1: Verificando se a aprovacao foi concedida.\n");
        assertTrue(aprovado);

        System.out.println("- Verificando se a instituicao eh nula.");
        boolean erroNulo = admin.aprovarInstituicao(null);
        System.out.println("  -> Validacao 2: Verificando se o sistema bloqueou a aprovacao.\n");
        assertFalse(erroNulo);
    }

    @Test
    public void testGerenciarUsuariosConsultarEAtualizar() {
        System.out.println("- Cadastrando usuario inicial.");
        boolean criado = admin.criarUsuario(usuarioDoador);
        System.out.println("  -> Validacao 1: Confirmando cadastro do usuario no sistema.\n");
        assertTrue(criado);

        System.out.println("- Buscando usuario pelo CPF/CNPJ.");
        Usuario encontrado = admin.buscarUsuarioPorCpfCnpj(usuarioDoador.getCpfCnpj());
        System.out.println("  -> Validacao 2: Verificando se o usuario foi localizado com o nome correto.\n");
        assertNotNull(encontrado);
        assertEquals(usuarioDoador.getNome(), encontrado.getNome());

        System.out.println("- Atualizando o nome do usuario cadastrado.");
        boolean atualizado = admin.atualizarNomeUsuario(usuarioDoador.getCpfCnpj(), "Supermercado Teste Novo Nome");
        System.out.println("  -> Validacao 3: Confirmando alteracao e o novo nome modificado.\n");
        assertTrue(atualizado);
        assertEquals("Supermercado Teste Novo Nome", encontrado.getNome());
    }
}