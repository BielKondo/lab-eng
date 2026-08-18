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
