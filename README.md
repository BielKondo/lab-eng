# Projeto_Lab_Eng_Software

Logística Dinâmica de Resgate de Perecíveis (Combate à Fome)

Os Atores do Sistema
O Doador (Mercado/Padaria): O usuário que tem o alimento. Ele não tem tempo. Se o processo de doação exigir mais do que três cliques, ele desiste e joga a comida fora.

O Receptor (ONG/Abrigo): O usuário que precisa do alimento. Operam com recursos escassos, dependem de voluntários para o transporte e precisam de previsibilidade.

O Motor de Roteamento (Seu Backend): O "ator" silencioso que cruza dados geográficos, tempo de validade e gerencia as transações concorrentes para garantir que ninguém perca a viagem.

O Fluxo Lógico e a Máquina de Estados
A vida útil de uma doação dentro do sistema não é apenas um registro no banco de dados, é uma máquina de estados finitos rigorosa.

Estado 1: Criação (Ingestão Rápida). O Doador abre o sistema e informa: "Lote de 50 pães franceses, validade em 4 horas". O sistema registra o lote com o status DISPONIVEL.

Estado 2: O Roteamento e Notificação. O backend calcula um raio de distância baseado no tempo de validade. Alimentos que vencem em 2 horas só notificam ONGs a 5km. Alimentos que vencem em 12 horas podem notificar ONGs a 20km.

Estado 3: A Corrida Crítica (Reserva). Múltiplas ONGs recebem o push. Duas apertam "Aceitar Lote" no mesmo milissegundo. Aqui entra o coração da sua engenharia: o sistema aplica um Lock transacional (otimista ou pessimista). Apenas uma requisição vence. O estado do lote muda para RESERVADO_PENDENTE_RETIRADA. A ONG perdedora recebe um erro claro informando que o lote já foi pego.

Estado 4: Timeout de Abandono (Job Assíncrono). Se a ONG reservou, mas não buscou em um tempo pré-determinado (ex: 50% do tempo de validade restante), um worker em background expira a reserva, penaliza a ONG (diminuindo seu score futuro) e volta o lote para DISPONIVEL.

Estado 5: O Handshake (Conclusão). A ONG chega ao local, lê um QR Code simples na tela do Doador ou insere um PIN de 4 dígitos. A transação atômica é efetivada. Lote muda para COLETADO.

Funcionalidades Core (Visão Modular)
Para não construir um monólito engessado, o domínio da aplicação deve ser dividido em módulos com responsabilidades únicas.

Gestão de Inventário e Validade: Módulo responsável apenas por cadastrar os lotes e monitorar o tempo de expiração de forma contínua.

Motor de Matchmaking Geoespacial: Módulo isolado que recebe coordenadas e retorna a lista de ONGs elegíveis, priorizando as que têm maior índice de coleta bem-sucedida ou menor distância.

Gerenciador de Concorrência e Reservas: A camada de infraestrutura que gerencia a fila de requisições, garantindo que o banco de dados nunca registre uma dupla reserva.

Sistema de Reputação e Punição (Score): Funcionalidade para lidar com o comportamento humano. Doadores que cancelam muito ou ONGs que reservam e não buscam perdem prioridade no algoritmo.

O Teste de Estresse (Perspectivas e Contrapontos)
Como seu parceiro de projeto, preciso apontar as falhas potenciais nessa lógica para você blindar sua arquitetura antes de escrever a primeira linha de código:

A Ilusão da Coleta Imediata: Estamos assumindo que a ONG tem um carro abastecido e um motorista a postos o tempo todo. A realidade: A logística é demorada. A sua lógica de roteamento precisa cruzar a distância com o trânsito médio, senão o alimento estraga no porta-malas do voluntário.

O Risco do "No-Show": O que acontece quando a ONG reserva e não aparece? O Doador fica frustrado e nunca mais usa o app. Seu sistema precisa de alertas escalonados. Se a ONG não validar o início da viagem em X minutos, a reserva deve cair automaticamente.

O Problema do Banco de Dados Travado: Se você usar as transações padrão do banco de dados relacional para lidar com a concorrência na hora da reserva, um pico de acessos pode gerar deadlocks ou derrubar o banco. Você precisará pensar se usará filas em memória, estratégias de banco adequadas, ou se essa validação ocorrerá antes de bater no disco.

## Backend (Spring Boot)

Requisitos: JDK 17+ e Maven 3.9+ (o IntelliJ já traz um Maven embutido).

    mvn clean verify      # compila e roda os testes (mesmo comando do Jenkins)
    mvn spring-boot:run   # sobe a API e o front-end; o console mostra "ResgatAI no ar: http://localhost:8080"

Ao subir, a aplicação cria dados de demonstração (`DadosIniciais`): administrador `529.982.247-25`,
doador `98.765.432/0001-98`, instituição aprovada `11.222.333/0001-81`, instituição em análise
`33.444.555/0001-81` e dois lotes. O console do banco fica em `/h2-console`
(JDBC URL `jdbc:h2:mem:resgatai`, usuário `sa`, sem senha).

### Pacotes (`src/main/java/br/mackenzie/resgatai`), um por módulo da arquitetura

- `usuario` — `Usuario`, `Estabelecimento` (Doador), `Instituicao` (Receptor), `Administrador` e `UsuarioService` (UC01, RF06)
  - **UC01 (`UsuarioService`):** listar; cadastrar (valida formato e dígitos de CNPJ/CPF, checa duplicidade, vincula permissões, inicia o score, envia o e-mail de primeiro acesso, audita); consultar/detalhar (3a); atualizar com auditoria dos campos (3b); inativar com confirmação e bloqueio se houver lotes ativos ou reservas em andamento (3c); exceções `DadosInvalidosException` (6a) e `UsuarioJaCadastradoException` (6b).
  - **Pré-condição:** toda operação recebe o documento do administrador que a executa. O primeiro administrador é criado por `criarAdministradorInicial`.
  - **Atores secundários:** `Notificador` (e-mail e push) e `BaseValidacaoInstituicoes` são interfaces. As implementações provisórias ficam em `comum/IntegracoesExternas`.
- `estoque` — `LoteAlimento`, a máquina de estados `StatusLote`, `Urgencia` e `LoteAlimentoService` (UC02, RF02, RF03)
  - **UC02 (`LoteAlimentoService.cadastrar`):** valida (9a campos obrigatórios, 5a validade passada, 7a janela depois do vencimento), associa o endereço de retirada do Doador ou o alternativo (7a), grava como `DISPONIVEL`, registra no log, inclui o UC03 e devolve o resumo com o indicador de urgência (passo 6: `indicadorUrgencia`). Repetir lote anterior (3a): `listarDoDoador` + `prepararRepeticao`.
  - **Busca (RF03 / UC04-2a):** `buscarDisponiveis` filtra por categoria, preço máximo, prazo de vencimento e raio a partir da localização do Receptor.
- `notificacao` — `MotorRoteamento`, `Notificacao` e `NotificacaoService` (UC03, RF07). Especificação completa em [`docs/UC03-Notificar-Alimentos-Proximos.md`](docs/UC03-Notificar-Alimentos-Proximos.md).
  - Raio por tempo restante: ≤ 2 h → 5 km, ≤ 6 h → 10 km, ≤ 12 h → 20 km, > 12 h → 30 km; sem elegíveis, dobra uma vez (teto 50 km). Distância por Haversine sobre as coordenadas cadastradas.
- `reserva` — `Reserva`, `StatusReserva` e `ReservaService` (UC04; UC05 e UC06 pendentes)
  - **UC04 (`ReservaService.reservar`):** bloqueio transacional com `SELECT … FOR UPDATE` (`LoteAlimentoRepository.buscarParaReserva`, `@Lock(PESSIMISTIC_WRITE)`); verifica o status; passa o lote a `RESERVADO_PENDENTE`; gera PIN de 6 dígitos e o conteúdo do QR Code; prazo limite = fim da janela do Doador; notifica o Doador. 6a `LoteIndisponivelException` (traz as outras ofertas); 6b `LoteExpiradoException` (o lote vira `EXPIRADO` mesmo com a exceção — `noRollbackFor`).
  - O teste `ReservaConcorrenciaTest` dispara 6 reservas simultâneas no mesmo lote e confere que exatamente uma vence.
- `api` — controladores REST (camada de apresentação) e `TratadorDeErros`, que converte as exceções dos fluxos em HTTP: 400 dados inválidos, 403 perfil sem permissão, 404 não encontrado, 409 conflito (UC01-6b, UC04-6a), 410 lote expirado (UC04-6b).
- `comum` — o que os módulos compartilham: exceções (`DadosInvalidosException`, `AcessoNegadoException`, `NaoEncontradoException`), log de auditoria (`RegistroAuditoria`), o ator externo `Notificador`, `IntegracoesExternas` e `Geo` (distância).

### API REST

Enquanto o caso de uso "Autenticar Usuário" não existe, quem chama se identifica pelo cabeçalho
`X-Documento` (CPF/CNPJ). A pré-condição de perfil é verificada pelos serviços.

| Método e caminho | Caso de uso |
|---|---|
| `GET /api/usuarios/eu` | identificação do usuário atual (tela Entrar) |
| `GET/POST /api/usuarios`, `GET /api/usuarios/busca`, `GET/PUT /api/usuarios/{doc}`, `GET /api/usuarios/{doc}/inativacao`, `DELETE /api/usuarios/{doc}`, `POST /api/usuarios/{doc}/aprovacao` (`{doc}` só com dígitos) | UC01, RF06 |
| `GET /api/lotes/urgencia?dataValidade=` | UC02 passo 6 |
| `POST /api/lotes`, `GET /api/lotes/meus`, `GET /api/lotes/{id}/repetir` | UC02 (principal, 3a) |
| `GET /api/lotes/disponiveis?categoria&precoMaximo&validadeAte&raioKm`, `GET /api/lotes/{id}` | UC04 2a e passo 3 (RF03) |
| `GET /api/notificacoes`, `POST /api/notificacoes/{id}/lida` | UC03 / UC04 passo 1 |
| `POST /api/reservas` `{"loteId": n}`, `GET /api/reservas`, `GET /api/reservas/{id}` | UC04 |

### Front-end (`src/main/resources/static`)

Página única em HTML/CSS/JavaScript, sem framework, servida pelo próprio Spring Boot e integrada à API
por `fetch`. Segue as telas do wireframe: Boas-vindas/Entrar, Ofertas perto (com filtros), Oferta → Reservar,
Reserva confirmada (QR Code + PIN + prazo), Minhas reservas e Notificações para o Receptor; Cadastrar lote
(com indicador de urgência, repetir lote anterior e endereço alternativo) e Meus lotes para o Doador;
Gestão de usuários para o Administrador. Os erros de validação (6a/9a) marcam os campos apontados
mantendo o que foi digitado; o 6a do UC04 mostra as outras ofertas disponíveis.

### Testes

| Classe | Cobertura |
|---|---|
| `usuario/UsuarioServiceTest`, `ValidadorUsuarioTest` | UC01 (principal, 3a, 3b, 3c, 6a, 6b), RF06 |
| `estoque/LoteAlimentoServiceTest`, `LoteAlimentoTest`, `UrgenciaTest` | UC02 (principal, 3a, 7a, 5a, 7a, 9a), busca RF03, máquina de estados |
| `notificacao/MotorRoteamentoTest`, `NotificacaoServiceTest` | UC03 (principal, 3a, 4a, 2a, 6a) |
| `reserva/ReservaServiceTest`, `ReservaConcorrenciaTest` | UC04 (principal, 2a, 4a, 6a, 6b) e a corrida crítica com threads |

### Decisões e pendências

- **Autenticação:** não especificada; identificação provisória pelo cabeçalho `X-Documento`.
- **Primeiro administrador:** só é criado por `UsuarioService.criarAdministradorInicial` (chamado por `DadosIniciais`). Com `resgatai.dados-iniciais=false` e banco vazio, nenhum endpoint do UC01 responde até existir um administrador.
- **Reserva exige instituição aprovada (RF06).** Uma instituição em análise vê as ofertas, mas não reserva.
- **"Dentro da janela de retirada" (UC04):** interpretado como "a janela ainda não se encerrou" — reservar antes do início da janela é permitido, porque a retirada acontece depois.
- **Prazo limite de retirada = fim da janela do Doador** (lacuna 12 do CONTEXTO).
- **Pagamento (RF04 / lacuna 1):** fora do escopo desta entrega; os lotes podem ter preço reduzido, mas a reserva não cobra.
- **UC05 e UC06:** não implementados. A `Reserva` já tem `status`, `expirarTimeout()` e o código de validação para eles.
