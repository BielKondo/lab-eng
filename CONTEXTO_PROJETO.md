# CONTEXTO DO PROJETO — Logística Dinâmica de Resgate de Perecíveis

> **Como usar este arquivo:** forneça-o à IA no início da conversa (ou como arquivo de contexto/instruções do projeto). Ele consolida toda a documentação de Engenharia de Software do projeto (demanda, requisitos, casos de uso, modelagem e arquitetura). Trate-o como a **fonte da verdade**. Em caso de dúvida ou lacuna, veja a seção [Lacunas e inconsistências conhecidas](#13-lacunas-e-inconsistências-conhecidas) antes de assumir algo.

---

## 1. Instruções para a IA

- Responda em **português do Brasil**, a menos que eu peça outro idioma.
- Use a terminologia deste documento (Doador, Receptor, Lote, Reserva, Motor de Roteamento, etc.) e os **nomes de status exatos** (`DISPONIVEL`, `RESERVADO_PENDENTE`, `COLETADO`, `EXPIRADO`, `CANCELADO`).
- Mantenha coerência com os artefatos já produzidos (requisitos RF/RNF, casos de uso UC01/UC02/UC04/UC06, diagramas de classes e sequência, arquitetura em camadas).
- Quando eu pedir novos artefatos (ex.: UC03, UC05, testes, código), siga o **mesmo formato e nível de detalhe** dos existentes (fluxo principal, alternativos e de exceção numerados em duas colunas: *Ações do Ator* / *Ações do Sistema*).
- Se algo que eu pedir conflitar com este documento, **aponte o conflito** em vez de resolver silenciosamente.
- Se faltar informação, **diga o que está assumindo** e pergunte antes de inventar regras de negócio.
- Priorize soluções simples e adequadas ao escopo de um projeto acadêmico, mas com boas práticas (código limpo, responsabilidade única, testabilidade).

---

## 2. Identificação do projeto

| Item | Valor |
|---|---|
| Instituição | Universidade Presbiteriana Mackenzie |
| Curso / Turma | Ciência da Computação — 6D |
| Disciplina | Engenharia de Software |
| Nome do projeto | Logística dinâmica de resgate de perecíveis |
| Nome do grupo | Biri Dins |
| Nome do app (wireframe) | **ResgatAI** |
| Natureza | Projeto extensionista (impacto social) |

**Integrantes**

- Gabriel Hideaquy Kondo — 10436238
- João Pedro Sinzato Bocchini — 10440034
- Arthur Roldan Slikta — 10353847
- Felipe Bertaco Haddad — 10437372
- Lucas Brezolini — 10439256

---

## 3. Visão geral e problema

Estabelecimentos comerciais de alimentos (mercados, padarias, hortifrútis) descartam produtos perecíveis próximos à validade. Ao mesmo tempo, ONGs, igrejas, abrigos e cozinhas solidárias têm orçamento escasso para alimentar pessoas em vulnerabilidade. Falta um **canal direto, rápido e eficiente** entre essas duas pontas.

**Solução:** plataforma **web/mobile** que funciona como um **marketplace B2B de impacto social**. Estabelecimentos cadastram lotes de alimentos próximos ao vencimento a preço reduzido; instituições cadastradas visualizam as ofertas em tempo real, reservam/compram e combinam a retirada presencial.

**Funcionalidades gerais:** gerenciamento de estoque, perfis distintos (fornecedor e consumidor), notificações geolocalizadas, reserva concorrente segura, validação de retirada por QR Code/PIN e painel de impacto gerado.

### Justificativa (três pilares)

1. **Econômico:** estabelecimentos recuperam parte do custo de itens que seriam descartados (nova linha de receita).
2. **Social:** as instituições compram mais com o mesmo orçamento e atendem mais pessoas.
3. **Ambiental:** reduz resíduos orgânicos em aterros.

---

## 4. Personagens / atores principais

| Ator | Exemplo | Características e restrições de design |
|---|---|---|
| **Doador** (Usuário Fornecedor) | Mercado, padaria, distribuidor | Tem pouco tempo. **Se o processo de doação exigir mais de ~3 cliques, ele desiste.** O cadastro de lote precisa ser rápido. |
| **Receptor** (Usuário Consumidor) | ONG, igreja, abrigo, cozinha solidária | Recursos escassos, depende de voluntários para transporte, precisa de **previsibilidade logística**. Voluntários podem ter **baixo letramento digital**. |
| **Motor de Roteamento** (Sistema) | Componente invisível | Cruza dados geográficos, prazos de validade e gerencia transações concorrentes. |
| **Administrador do Sistema** | Equipe da plataforma | Gerencia usuários, aprova cadastros de instituições, mantém tabelas de segurança. |
| **Atores secundários** | Serviço de E-mail, Base de Validação de Instituições, Sistema de Notificação, Worker Assíncrono | Suporte aos casos de uso. |

**Envolvidos indiretos:** pessoas em insegurança alimentar (beneficiárias finais), sociedade e meio ambiente.

> **Nota de nomenclatura:** "Doador" = "Estabelecimento (Comercial)" = "Fornecedor". "Receptor" = "Instituição (Social)" = "Consumidor". Os casos de uso usam Doador/Receptor; o diagrama de classes usa `Estabelecimento`/`Instituicao`.

---

## 5. Etapas do projeto

1. **Engenharia de Requisitos** — requisitos funcionais/não funcionais, casos de uso, backlog.
2. **Design e Prototipação** — wireframes, UX/UI.
3. **Modelagem e Arquitetura** — banco de dados, arquitetura modularizada (princípio de responsabilidade única).
4. **Desenvolvimento** — API (back-end) e interface (front-end).
5. **Testes e Infraestrutura** — casos de teste, CI/CD, deploy em nuvem (**AWS**).

---

## 6. Critérios de qualidade

- **Usabilidade:** interface intuitiva e acessível (público com baixo letramento digital).
- **Confiabilidade e desempenho:** estoque atualizado em tempo real para que nenhuma instituição se desloque para um lote já reservado por outra.
- **Manutenibilidade:** código limpo e bem estruturado, permitindo adicionar relatórios de métricas de impacto e integração com meios de pagamento nas próximas iterações.
- **Segurança:** proteção dos dados cadastrais e validação de identidade para que **apenas instituições legítimas** acessem os descontos.

---

## 7. Requisitos

### 7.1 Funcionais

| ID | Requisito |
|---|---|
| **RF01** | Cadastro e gerenciamento de perfil para Estabelecimentos Comerciais e Instituições Sociais (ONGs, igrejas, abrigos). |
| **RF02** | O estabelecimento cadastra lotes de perecíveis com: **nome, quantidade, preço reduzido, foto e data limite de validade**. |
| **RF03** | A instituição busca e filtra lotes por **geolocalização, categoria, faixa de preço e prazo de vencimento**. |
| **RF04** | Reserva e **liquidação do pagamento** do lote diretamente pela plataforma. |
| **RF05** | Geração de **comprovante digital único com QR Code** para validar/confirmar a retirada no estabelecimento. |
| **RF06** | Validação e aprovação do cadastro de instituições pelo **Administrador** antes da liberação total de acesso. |
| **RF07** | Notificações em tempo real (push/e-mail): aos estabelecimentos sobre novas reservas e às ONGs sobre novos lotes na sua região. |

### 7.2 Não funcionais

| ID | Categoria | Requisito |
|---|---|---|
| **RNF01** | Usabilidade | Interface responsiva (dispositivos móveis e navegadores web). |
| **RNF02** | Confiabilidade | Remover automaticamente da listagem pública lotes vencidos ou com tempo de reserva expirado. |
| **RNF03** | Desempenho | Busca e filtros por geolocalização em **≤ 5 segundos**. |
| **RNF04** | Questões legais | Conformidade com a **LGPD**. |
| **RNF05** | Desempenho / disponibilidade | **SLA de 99,5% de uptime**. |

---

## 8. Casos de uso

### 8.1 Visão geral

| ID | Nome | Ator principal | Resumo |
|---|---|---|---|
| **UC01** | Gerenciar Usuários (CRUD) | Administrador | Criar, consultar, atualizar, inativar perfis de Doadores/Receptores/Admins; manter tabelas de segurança. |
| **UC02** | Cadastrar Lote de Alimentos | Doador | Registra rapidamente um lote próximo ao vencimento; status inicial `DISPONIVEL`. |
| **UC03** | Notificar Alimentos Próximos | Motor de Roteamento (Sistema) | Calcula raio de alcance com base no tempo de validade restante e envia push às instituições elegíveis próximas. *(Incluído por UC02; **sem especificação detalhada ainda**.)* |
| **UC04** | Reservar Lote de Alimentos | Receptor | Reserva com **bloqueio transacional (lock)**; status → `RESERVADO_PENDENTE`; gera PIN/QR Code. |
| **UC05** | Expirar Reserva por Timeout | Sistema (Worker Assíncrono) | Se a ONG não comparecer/validar no prazo, cancela a reserva e o lote volta a `DISPONIVEL`. *(**Sem especificação detalhada ainda**.)* |
| **UC06** | Validar Retirada de Alimentos | Doador (com Receptor presente) | Doador valida PIN/QR no resgate presencial; status → `COLETADO`. *(No texto é citado como "CU06".)* |

### 8.2 Relacionamentos (diagramas de casos de uso)

- **UC01:** `«include»` Vincular perfil de acesso, Registrar log de auditoria; `«extend»` Validar instituição (Base de validação), Enviar credenciais de acesso (Serviço de e-mail).
- **UC02:** `«include»` Autenticar usuário, Notificar alimentos próximos (UC03); `«extend»` Repetir lote anterior, Informar endereço alternativo.
- **UC04:** `«include»` Autenticar usuário, Notificar doador; `«extend»` Buscar lotes disponíveis. Atores: Receptor, Doador, Sistema de notificação.
- **UC06:** `«include»` Autenticar usuário, Notificar receptor; `«extend»` Digitar PIN manualmente. Atores: Doador, Receptor, Sistema de notificação.

### 8.3 UC01 — Gerenciar Usuários (CRUD)

- **Pré-condição:** administrador autenticado com perfil ativo.
- **Pós-condição:** perfil criado/consultado/atualizado/inativado; permissões vinculadas; log de auditoria gravado; credenciais de primeiro acesso enviadas.
- **Fluxo principal (criar):** admin abre módulo → sistema lista usuários → admin escolhe "criar" → formulário (identificação, contato, endereço, tipo de perfil: Doador/Receptor/Administrador) → admin confirma → sistema valida formato e unicidade de CNPJ/CPF e e-mail → persiste, vincula perfil/permissões e **inicializa o score de reputação** → envia e-mail de primeiro acesso (link de definição de senha) → registra log de auditoria e retorna à lista.
- **Alternativos:** 3a consultar (critérios: nome, CNPJ/CPF, perfil, situação; exibe dados, perfil de acesso, **score de reputação** e histórico); 3b atualizar (valida, grava e audita autor/data/campos); 3c remover = **inativar** (verifica lotes ativos/reservas em andamento, confirma, revoga permissões, **preserva histórico**).
- **Exceções:** 6a dados obrigatórios ausentes/inválidos (mantém dados já informados); 6b CNPJ/CPF ou e-mail já cadastrado (oferece atualizar o existente ou cancelar).

### 8.4 UC02 — Cadastrar Lote de Alimentos

- **Pré-condição:** Doador autenticado, cadastro ativo, endereço de retirada validado.
- **Pós-condição:** lote gravado com status `DISPONIVEL`, vinculado ao Doador, janela de retirada registrada, Receptores compatíveis notificados, lote visível nas buscas, operação logada.
- **Fluxo principal:** seleciona "cadastrar novo lote" → formulário (tipo de alimento, categoria, quantidade, unidade de medida, data de validade, condições de conservação, janela de retirada, observações) → sistema **calcula prazo restante e mostra indicador de urgência** → Doador confirma → sistema valida, gera ID do lote, atribui `DISPONIVEL`, associa ao endereço de retirada → persiste e disponibiliza → inclui **UC03** (notifica elegíveis) → exibe resumo do lote publicado.
- **Alternativos:** 3a repetir lote anterior (pré-preenche tudo **exceto quantidade e validade**); 7a endereço de retirada diferente (vale só para o lote).
- **Exceções:** 5a data de validade anterior à atual (lotes vencidos não são publicados); 7a janela de retirada posterior ao vencimento; 9a campos obrigatórios ausentes.

### 8.5 UC04 — Reservar Lote de Alimentos

- **Pré-condição:** Receptor autenticado e ativo; lote `DISPONIVEL` e dentro da janela de retirada.
- **Pós-condição:** lote `RESERVADO_PENDENTE` vinculado ao Receptor; PIN/QR Code gerado e disponível às duas partes; prazo limite de retirada registrado; Doador notificado; lote removido das buscas dos demais.
- **Fluxo principal:** Receptor vê notificação → seleciona lote → sistema mostra detalhes → Receptor escolhe reservar → **sistema aplica bloqueio transacional** no registro do lote → verifica que o status ainda é `DISPONIVEL` → altera para `RESERVADO_PENDENTE`, vincula reserva e registra data/hora → gera PIN + QR Code e define prazo limite (com base na janela do Doador) → confirma transação e **libera o lock** → remove o lote das listagens dos demais → notifica Doador e mostra confirmação ao Receptor.
- **Alternativos:** 2a localizar lote por busca (filtrado por perfil e raio de atuação); 4a desistir antes de confirmar (sem lock, sem mudança).
- **Exceções:** 6a lote já reservado por outro (rollback, libera lock, informa e sugere outros lotes); 6b lote vencido/fora da janela (rollback, status → `EXPIRADO`, retira das listagens, notifica Doador).
- **Regra crítica:** vence a **primeira requisição bem-sucedida**; as demais recebem aviso de indisponibilidade.

### 8.6 UC03 — Notificar Alimentos Próximos (especificado em `docs/UC03-Notificar-Alimentos-Proximos.md`)

- **Atores:** Motor de Roteamento (principal); Sistema de Notificação e Receptor (secundários). Incluído por UC02 (passo 11).
- **Fluxo principal:** calcula o tempo restante → define o raio (≤ 2 h: 5 km; ≤ 6 h: 10 km; ≤ 12 h: 20 km; > 12 h: 30 km) → seleciona instituições ativas, aprovadas, com localização, dentro do raio (e do raio de atuação próprio) → ordena por distância (desempate: score) → envia e registra cada notificação → devolve o resumo ao UC02.
- **Alternativos:** 3a lote sem coordenadas (não notifica; lote segue nas buscas); 4a nenhum elegível (dobra o raio uma vez, teto 50 km).
- **Exceções:** 2a lote não está mais `DISPONIVEL`; 6a falha de envio a um receptor (registra `FALHA` e continua).

### 8.7 UC06 — Validar Retirada de Alimentos

- **Pré-condição:** Doador autenticado; lote `RESERVADO_PENDENTE`, dentro do prazo, com código vigente; Receptor presente com o código.
- **Pós-condição:** lote `COLETADO`; data, hora e responsável registrados; **código invalidado** (sem reuso); reputação de Doador e Receptor atualizada; quantidade somada aos **indicadores de impacto**; Receptor notificado.
- **Fluxo principal:** Receptor apresenta PIN/QR → Doador escolhe "validar retirada" → tela de leitura de QR (ou digitação de PIN) → sistema verifica existência/vigência do código e identifica lote + Receptor → verifica status e prazo → mostra dados para conferência → Doador confirma a entrega → status `COLETADO` + registro → invalida código → atualiza reputação e impacto → notifica Receptor e emite comprovante ao Doador.
- **Alternativo:** 4a leitura de QR indisponível → digitação manual do PIN, registra a **quantidade efetivamente entregue**, anota divergência e conta no impacto só o entregue.
- **Exceções:** 5a código inválido/inexistente; 5b código já utilizado (mostra data/hora/responsável anterior); 6a prazo expirado (Doador decide autorizar com marcação de atraso ou recusar — se recusar, o lote fica para a rotina de expiração); 7a divergência lote × entrega (Doador cancela com justificativa → status `CANCELADO`, Receptor notificado).

---

## 9. Máquina de estados do `LoteAlimento`

```
            cadastro (UC02)
                 │
                 ▼
          ┌─────────────┐   validade/janela vencida (UC04-6b / RNF02)   ┌──────────┐
          │ DISPONIVEL  │ ───────────────────────────────────────────► │ EXPIRADO │
          └─────────────┘                                               └──────────┘
             │       ▲
 reserva com │       │ timeout do prazo de retirada (UC05)
 lock (UC04) ▼       │
       ┌──────────────────────┐   validação PIN/QR (UC06)   ┌──────────┐
       │  RESERVADO_PENDENTE  │ ──────────────────────────► │ COLETADO │
       └──────────────────────┘                             └──────────┘
                 │
                 │ Doador cancela por divergência (UC06-7a)
                 ▼
            ┌───────────┐
            │ CANCELADO │
            └───────────┘
```

| Transição | Gatilho | Caso de uso |
|---|---|---|
| (novo) → `DISPONIVEL` | Doador publica lote | UC02 |
| `DISPONIVEL` → `RESERVADO_PENDENTE` | Receptor reserva (com lock) | UC04 |
| `DISPONIVEL` → `EXPIRADO` | Validade/janela vencida | UC04 (6b) / RNF02 |
| `RESERVADO_PENDENTE` → `COLETADO` | Doador valida PIN/QR | UC06 |
| `RESERVADO_PENDENTE` → `DISPONIVEL` | Timeout sem retirada | UC05 |
| `RESERVADO_PENDENTE` → `CANCELADO` | Doador cancela por divergência | UC06 (7a) |

---

## 10. Modelagem

### 10.1 Diagrama de classes

**Hierarquia:** `Instituicao`, `Administrador` e `Estabelecimento` **especializam** `Usuario`.

| Classe | Atributos | Métodos |
|---|---|---|
| `Usuario` | `documento: String`, `nome: String`, `endereco: String`, `telefone: String`, `email: String`, `senha: String` | `login(): void` |
| `Instituicao` (extends Usuario) | `statusAnalise: String` | `buscarOfertas(): void`, `reservarLote(): void` |
| `Estabelecimento` (extends Usuario) | — | `cadastrarLote(): void`, `validarRetirada(): void` |
| `Administrador` (extends Usuario) | — | `aprovarInstituicao(): void`, `gerenciarUsuarios(): void` |
| `LoteAlimento` | `id: int`, `nome: String`, `categoria: String`, `quantidade: int`, `precoReduzido: double`, `foto: String`, `dataValidade: Date` | `alterarStatus(): void` |
| `Reserva` | `id: int`, `dataHora: Date`, `codigoValidacao: String`, `status: String` | `gerarCodigo(): void`, `expirarTimeout(): void` |
| `Pagamento` | `id: int`, `valorTotal: double`, `statusPagamento: String` | `processarPagamento(): void` |

**Relacionamentos:**

- `Estabelecimento` 1 — * `LoteAlimento` (um estabelecimento publica vários lotes).
- `Instituicao` 1 — * `Reserva` (uma instituição faz várias reservas).
- `Reserva` — `LoteAlimento` (cada reserva refere-se a um lote).
- `Reserva` — `Pagamento` (cada reserva tem um pagamento associado).

### 10.2 Diagrama de sequência (4 cenários)

Participantes: Administrador, Doador (Mercado), Receptor (ONG) → `:Interface` (Front-End) → `:Controlador`, `:Usuario`, `:LoteAlimentos`, `:Reserva` (Back-End / Regras de Negócio) → Banco de Dados.

1. **Gestão de Usuário (UC01):** Admin cadastra ONG/Doador → `cadastrarUsuario()` → `new Usuario()` → `salvar()` → "Usuário cadastrado".
2. **Cadastro de Lote (UC02):** Doador cadastra lote → `cadastrarLote(dados)` → `new LoteAlimentos()` → `salvar()` (Status: `DISPONIVEL`) → "Lote publicado".
3. **Reserva com Concorrência (UC04):** Receptor solicita reserva → `reservarLote(idLote)` → **Lock transacional (bloqueio)** → `alterarStatus("RESERVADO_PENDENTE")` → `new Reserva()` → `gerarCodigo(PIN/QR)` → salvar reserva e **liberar lock** → confirmação + exibe QR Code.
4. **Validação e Retirada Presencial (UC06):** Receptor apresenta QR presencialmente → Doador escaneia/digita PIN → `validarRetirada(codigo)` → `verificarVigencia()` → `alterarStatus("COLETADO")` → atualizar status → "Entrega confirmada!".

---

## 11. Wireframes (app **ResgatAI**, tela de 393×852)

| Fluxo | Telas |
|---|---|
| Boas-vindas / navegação | **01 Boas-vindas** ("Bem-vindo ao ResgatAI": Entrar, Criar conta, Entrar com [login social]) → **02 Feed** ("Ofertas perto": busca, filtros, cards com foto, navegação inferior) → **03 Mapa** ("Onde retirar": alternância Lista/Mapa, pins, botão "Ver oferta") |
| Cadastro | **01 Perfil** ("Qual é o seu perfil?": Estabelecimento / Instituição / **Pessoa física**) → **02 Formulário** ("Seus dados": CNPJ/CPF, Nome, Endereço, Telefone, E-mail, Senha, upload de documento) → **03 Verificação** ("Conta em análise", botão "Voltar ao início") |
| Reserva e retirada | **01 Oferta** (foto, descrição, preço, botão "Reservar") → **02 Pagamento** ("Revisar retirada": itens, horários de retirada ex. 14h/16h, opções de pagamento, total, "Confirmar") → **03 Retirada** ("Reserva confirmada": QR Code **ou** PIN de 6 dígitos, "Minhas reservas") |

---

## 12. Arquitetura

### 12.1 Padrão arquitetural

**Arquitetura em camadas** integrada a conceitos de **MVC**, com sistema dividido em **módulos independentes**: **estoque, rotas geográficas, controle de acessos simultâneos e reputação**.

### 12.2 Arquitetura lógica

1. **Camada de apresentação (front-end):** interfaces web/mobile para Doador (Estabelecimento), Receptor (Instituição social) e Administrador.
2. **Camada de negócios (back-end):**
   - **Controladoras:** interpretam requisições (cadastrar lote, solicitar reserva, validar retirada) e orquestram as regras.
   - **Modelos (entidades):** `Usuario`, `LoteAlimentos`, `Reserva`, `Pagamento`; encapsulam regras como geração de PIN/QR Code e cálculo de validade.
   - **Serviços em background:** workers assíncronos para **roteamento geográfico**, **notificações em tempo real** e **expiração de reservas (timeout)**.
3. **Camada de persistência / banco de dados:** acesso a dados (leitura/escrita), **bloqueio transacional (lock)** para reservas concorrentes e banco com isolamento para conformidade com a LGPD.

### 12.3 Arquitetura física

- **Nós clientes:** smartphones (app móvel) e computadores (navegador web), comunicando via **HTTPS**.
- **Nó servidor de aplicação (AWS):** API back-end com controladoras e regras de negócio, implantada por esteira **CI/CD**.
- **Nó servidor de banco de dados (AWS):** alta disponibilidade e isolamento (LGPD).

---

## 13. Lacunas e inconsistências conhecidas

> Pontos que a documentação atual **não resolve** ou onde há divergência entre artefatos. Ao trabalhar nesses temas, **sinalize e peça decisão** em vez de presumir.

1. **Doação × venda:** o texto fala em "doação", mas RF04 e a tela de pagamento tratam de **compra a preço reduzido** (marketplace B2B). A integração com meios de pagamento também aparece como evolução futura (Manutenibilidade). *Definir se o pagamento está no escopo da primeira entrega.*
2. **UC05** está listado e referenciado (timeout), mas **não tem especificação detalhada**. *(UC03 foi especificado em `docs/UC03-Notificar-Alimentos-Proximos.md` e está implementado.)*
3. **Numeração:** os casos de uso saltam de UC04 para UC06 nos diagramas; o texto alterna "UC" e "CU" (ex.: "CU03", "CU06").
4. **UC01 — referência quebrada:** a exceção 6b remete ao "fluxo alternativo 5a", que não existe (os alternativos são 3a, 3b, 3c).
5. **Pessoa física:** o wireframe oferece o perfil "Pessoa física", mas **não há requisito, ator ou classe** para esse perfil.
6. **Score de reputação:** citado em UC01 e UC06 e como módulo da arquitetura, mas **ausente do diagrama de classes** (sem atributo/classe) e sem regra de cálculo definida.
7. **Painel de impacto / indicadores:** citado na visão geral e em UC06, mas **sem requisito funcional** nem classe correspondente.
8. **Log de auditoria e tabelas de segurança/permissões:** citados em UC01, mas não modelados no diagrama de classes.
9. **Perfil de acesso do Administrador:** UC01 prevê criar Administrador, mas o RF01 só menciona Estabelecimentos e Instituições.
10. **Classe `Reserva` × `Pagamento` × `LoteAlimento`:** o status do lote (`DISPONIVEL`, etc.) **não aparece como atributo** em `LoteAlimento` (há só o método `alterarStatus()`); `Reserva.status` também não tem domínio de valores definido. Campos de UC02 (unidade de medida, tipo/categoria, condições de conservação, janela de retirada, observações, endereço alternativo) **não estão** na classe `LoteAlimento`.
11. **RF03 × UC04:** filtros por preço/geolocalização estão em RF03. *Regra adotada no código para o UC03 (`MotorRoteamento`): validade restante ≤ 2 h → 5 km; ≤ 6 h → 10 km; ≤ 12 h → 20 km; > 12 h → 30 km; sem elegíveis, dobra uma vez até 50 km. Distância por Haversine sobre coordenadas cadastradas (sem geocodificação automática).*
12. **Valor do timeout de reserva:** não há prazo numérico; UC04 diz que o prazo limite deriva da **janela de retirada informada pelo Doador**. *Adotado no código: prazo limite = fim da janela de retirada do lote.*
13. **Divergência de quantidade (UC06-4a):** o passo registra "quantidade entregue", mas o fluxo não detalha como o Doador informa esse valor.
14. **Tecnologias de implementação** (linguagem, framework, banco de dados, serviço de push, mapa/geocodificação, gateway de pagamento) **ainda não foram definidas**; apenas AWS, CI/CD e HTTPS estão decididos.

---

## 14. Glossário

| Termo | Significado |
|---|---|
| **Lote** | Conjunto de itens perecíveis cadastrado por um Doador com mesma validade/condições. |
| **Janela de retirada** | Intervalo em que o Receptor pode buscar o lote no endereço do Doador. |
| **Reserva** | Vínculo exclusivo entre um Receptor e um lote, com PIN/QR e prazo limite. |
| **PIN/QR Code** | Código de validação gerado na reserva e usado na retirada presencial; invalidado após o uso. |
| **Lock transacional** | Bloqueio do registro do lote durante a reserva para garantir exclusividade (primeira requisição vence). |
| **Motor de Roteamento** | Componente que cruza geolocalização, validade e concorrência para notificar/atribuir lotes. |
| **Worker assíncrono** | Processo em background (ex.: expiração de reservas, notificações). |
| **Score de reputação** | Indicador de confiabilidade de Doadores e Receptores, atualizado após retiradas. |
| **LGPD** | Lei Geral de Proteção de Dados (Lei nº 13.709/2018). |
| **SLA** | Acordo de nível de serviço (aqui: 99,5% de uptime). |

---

## 15. Prompt sugerido para iniciar a conversa

> Você é um assistente de Engenharia de Software e vai me ajudar no projeto descrito no arquivo `CONTEXTO_PROJETO.md`. Use-o como fonte da verdade, siga os formatos dos artefatos existentes e sinalize qualquer conflito ou lacuna antes de assumir algo. Minha tarefa agora é: **[descreva aqui o que você precisa]**.
