public class Instituicao {
    private String nome;
    private String cnpj;
    private boolean aprovada;

    public Instituicao(String nome, String cnpj) {
        this.nome = nome;
        this.cnpj = cnpj;
        this.aprovada = false;
    }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getCnpj() { return cnpj; }
    public void setCnpj(String cnpj) { this.cnpj = cnpj; }
    public boolean isAprovada() { return aprovada; }
    public void setAprovada(boolean aprovada) { this.aprovada = aprovada; }
}