package br.mackenzie.resgatai.usuario;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional // cada teste roda numa transação desfeita ao final, então o banco começa limpo
public class UsuarioServiceTest {

    @Autowired
    private UsuarioService service;

    private Estabelecimento usuarioDoador;
    private Instituicao ongValida;

    @BeforeEach
    public void setUp() {
        usuarioDoador = new Estabelecimento("98.765.432/0001-10", "Supermercado Teste", "contato@teste.com");
        ongValida = new Instituicao("12.345.678/0001-90", "ONG Teste", "contato@ong.com");
    }

    @Test
    public void testGerenciarUsuariosCriarCamposObrigatorios() {
        System.out.println("- Tentando criar usuario com dados validos.");
        boolean criadoSucesso = service.criar(usuarioDoador);
        System.out.println("  -> Validacao 1: Confirmando que o usuario foi cadastrado com sucesso.\n");
        assertTrue(criadoSucesso);

        System.out.println("- Tentando cadastrar um usuario nulo.");
        boolean criadoNulo = service.criar(null);
        System.out.println("  -> Validacao 2: Confirmando que o sistema bloqueou o cadastro nulo.\n");
        assertFalse(criadoNulo);

        System.out.println("- Tentando cadastrar um usuario sem e-mail.");
        boolean criadoSemEmail = service.criar(new Estabelecimento("11.111.111/0001-11", "Padaria", " "));
        System.out.println("  -> Validacao 3: Confirmando que o sistema bloqueou o cadastro incompleto.\n");
        assertFalse(criadoSemEmail);
    }

    @Test
    public void testGerenciarUsuariosCriarValidoEDuplicado() {
        System.out.println("- Cadastrando o primeiro usuário no sistema.");
        boolean primeiroCriado = service.criar(usuarioDoador);
        System.out.println("  -> Validacao 1: Confirmando que o primeiro usuario foi cadastrado.\n");
        assertTrue(primeiroCriado);

        System.out.println("- Tentando cadastrar segundo usuario com o mesmo CNPJ/CPF.");
        Usuario documentoDuplicado = new Estabelecimento("98.765.432/0001-10", "Outro Supermercado", "outro@email.com");
        boolean resultadoDocumento = service.criar(documentoDuplicado);
        System.out.println("  -> Validacao 2: Verificando que o cadastro duplicado foi rejeitado.\n");
        assertFalse(resultadoDocumento);

        System.out.println("- Tentando cadastrar segundo usuario com o mesmo e-mail.");
        Usuario emailDuplicado = new Estabelecimento("22.222.222/0001-22", "Outro Supermercado", "contato@teste.com");
        boolean resultadoEmail = service.criar(emailDuplicado);
        System.out.println("  -> Validacao 3: Verificando que o cadastro duplicado foi rejeitado.\n");
        assertFalse(resultadoEmail);
    }

    @Test
    public void testGerenciarUsuariosInativar() {
        System.out.println("- Cadastrando usuario inicial.");
        boolean criado = service.criar(usuarioDoador);
        System.out.println("  -> Validacao 1: Confirmando que o usuario foi criado e esta ativo por padrao.\n");
        assertTrue(criado);
        assertTrue(usuarioDoador.isAtivo());

        System.out.println("- Executando comando de inativacao por CPF/CNPJ.");
        boolean inativado = service.inativar(usuarioDoador.getDocumento());
        System.out.println("  -> Validacao 2: Confirmando que o status do usuario mudou para inativo (false).\n");
        assertTrue(inativado);
        assertFalse(usuarioDoador.isAtivo());
    }

    @Test
    public void testAprovarInstituicaoSucessoEErro() {
        service.criar(ongValida);
        service.criar(usuarioDoador);

        System.out.println("- Verificando se instituicao esta valida para aprovacao.");
        assertEquals(Instituicao.StatusAnalise.EM_ANALISE, ongValida.getStatusAnalise());
        boolean aprovado = service.aprovarInstituicao(ongValida.getDocumento());
        System.out.println("  -> Validacao 1: Verificando se a aprovacao foi concedida.\n");
        assertTrue(aprovado);
        assertEquals(Instituicao.StatusAnalise.APROVADA, ongValida.getStatusAnalise());

        System.out.println("- Tentando aprovar um usuario que nao eh instituicao.");
        boolean erroNaoInstituicao = service.aprovarInstituicao(usuarioDoador.getDocumento());
        System.out.println("  -> Validacao 2: Verificando se o sistema bloqueou a aprovacao.\n");
        assertFalse(erroNaoInstituicao);

        System.out.println("- Tentando aprovar um documento inexistente.");
        boolean erroInexistente = service.aprovarInstituicao("00.000.000/0000-00");
        System.out.println("  -> Validacao 3: Verificando se o sistema bloqueou a aprovacao.\n");
        assertFalse(erroInexistente);
    }

    @Test
    public void testGerenciarUsuariosConsultarEAtualizar() {
        System.out.println("- Cadastrando usuario inicial.");
        boolean criado = service.criar(usuarioDoador);
        System.out.println("  -> Validacao 1: Confirmando cadastro do usuario no sistema.\n");
        assertTrue(criado);

        System.out.println("- Buscando usuario pelo CPF/CNPJ.");
        Usuario encontrado = service.buscarPorDocumento(usuarioDoador.getDocumento()).orElse(null);
        System.out.println("  -> Validacao 2: Verificando se o usuario foi localizado com o nome correto.\n");
        assertNotNull(encontrado);
        assertEquals(usuarioDoador.getNome(), encontrado.getNome());

        System.out.println("- Atualizando o nome do usuario cadastrado.");
        boolean atualizado = service.atualizarNome(usuarioDoador.getDocumento(), "Supermercado Teste Novo Nome");
        System.out.println("  -> Validacao 3: Confirmando alteracao e o novo nome modificado.\n");
        assertTrue(atualizado);
        assertEquals("Supermercado Teste Novo Nome", encontrado.getNome());
    }
}
