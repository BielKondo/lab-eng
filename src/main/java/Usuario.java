public class Usuario {
    private String nome;
    private String cpfCnpj;
    private String email;
    private String perfil;
    private boolean ativo;

    public Usuario(String nome, String cpfCnpj, String email, String perfil) {
        this.nome = nome;
        this.cpfCnpj = cpfCnpj;
        this.email = email;
        this.perfil = perfil;
        this.ativo = true; // Por padrão o utilizador é criado ativo
    }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getCpfCnpj() { return cpfCnpj; }
    public void setCpfCnpj(String cpfCnpj) { this.cpfCnpj = cpfCnpj; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPerfil() { return perfil; }
    public void setPerfil(String perfil) { this.perfil = perfil; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
}