import java.util.ArrayList;
import java.util.List;

public class Administrador {
    private List<Usuario> usuarios;

    public Administrador() {
        this.usuarios = new ArrayList<>();
    }

    public boolean criarUsuario(Usuario usuario) {
        if (usuario == null || usuario.getCpfCnpj() == null) {
            return false;
        }
        for (Usuario u : usuarios) {
            if (u.getCpfCnpj().equals(usuario.getCpfCnpj())) {
                return false; // Rejeita duplicados
            }
        }
        usuarios.add(usuario);
        return true;
    }

    public Usuario buscarUsuarioPorCpfCnpj(String cpfCnpj) {
        if (cpfCnpj == null) return null;
        for (Usuario u : usuarios) {
            if (u.getCpfCnpj().equals(cpfCnpj)) {
                return u;
            }
        }
        return null;
    }

    public boolean inativarUsuario(String cpfCnpj) {
        Usuario u = buscarUsuarioPorCpfCnpj(cpfCnpj);
        if (u != null) {
            u.setAtivo(false);
            return true;
        }
        return false;
    }

    public boolean atualizarNomeUsuario(String cpfCnpj, String novoNome) {
        Usuario u = buscarUsuarioPorCpfCnpj(cpfCnpj);
        if (u != null && novoNome != null && !novoNome.trim().isEmpty()) {
            u.setNome(novoNome);
            return true;
        }
        return false;
    }

    public boolean aprovarInstituicao(Instituicao instituicao) {
        if (instituicao == null || instituicao.getCnpj() == null) {
            return false;
        }
        instituicao.setAprovada(true);
        return true;
    }

    public List<Usuario> getUsuarios() {
        return usuarios;
    }

}