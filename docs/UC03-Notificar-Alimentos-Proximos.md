# Especificação de caso de uso — UC03

| Especificação de caso de uso | |
|---|---|
| **Identificador** | UC03 |
| **Nome** | Notificar Alimentos Próximos |
| **Atores** | Principal: Motor de Roteamento (Sistema); Outros: Sistema de Notificação, Receptor |
| **Sumário** | Ao ser publicado um lote (UC02), o Motor de Roteamento calcula o tempo de validade restante, define o raio de alcance proporcional a esse tempo, seleciona as instituições elegíveis nas proximidades (ativas, aprovadas e dentro do raio) e envia a cada uma a notificação push com os dados do lote. Cada envio fica registrado para consulta pelo Receptor, que o visualiza no início do UC04. |
| **Pré-condição** | Lote gravado com status `DISPONIVEL` e vinculado ao Doador (UC02, passo 10). |
| **Pós-condição** | Notificações enviadas aos Receptores elegíveis e registradas (destinatário, lote, distância, data/hora e situação do envio). Resumo do envio (raio utilizado e quantidade de notificados) devolvido ao UC02. O lote permanece disponível nas buscas independentemente do resultado do envio. |
| **Pontos de Inclusão** | Incluído por UC02 — Cadastrar Lote de Alimentos (passo 11). |
| **Pontos de Extensão** | |

## Fluxo Principal

| Ações do Ator | Ações do Sistema |
|---|---|
| 1. UC02 aciona o Motor de Roteamento com o lote recém-publicado. | |
| | 2. Calcula o tempo restante até a data de validade do lote. |
| | 3. Define o raio de alcance com base no tempo restante: até 2 horas → 5 km; até 6 horas → 10 km; até 12 horas → 20 km; acima de 12 horas → 30 km. |
| | 4. Seleciona os Receptores elegíveis: instituições ativas, com cadastro aprovado pelo Administrador (RF06), com localização cadastrada, situadas dentro do raio de alcance e dentro do próprio raio de atuação, quando informado. |
| | 5. Ordena os elegíveis da instituição mais próxima para a mais distante, usando o score de reputação como critério de desempate. |
| | 6. Para cada elegível, monta a mensagem (doador, tipo e quantidade do alimento, preço ou doação, distância, tempo até o vencimento, janela e endereço de retirada) e a envia pelo Sistema de Notificação. |
| | 7. Registra cada notificação com destinatário, lote, distância, data/hora e situação `ENVIADA`. |
| | 8. Devolve ao UC02 o resumo do envio (raio utilizado, se houve ampliação, quantidade de notificados e de falhas). Caso de uso encerrado. |

## Fluxo Alternativo 3a. Lote sem localização

| Ações do Ator | Ações do Sistema |
|---|---|
| | 1. Constata que o endereço de retirada do lote não possui coordenadas (endereço alternativo informado sem localização — UC02-7a) e que a distância não pode ser calculada. |
| | 2. Registra o motivo no resumo, sem enviar notificações. O lote permanece visível nas buscas por categoria, preço e validade. Retorna ao passo 8 do fluxo principal. |

## Fluxo Alternativo 4a. Nenhum Receptor elegível no raio

| Ações do Ator | Ações do Sistema |
|---|---|
| | 1. Constata que nenhuma instituição atende aos critérios do passo 4. |
| | 2. Amplia o raio de alcance uma única vez, para o dobro do valor, limitado a 50 km, e retorna ao passo 4 do fluxo principal. |
| | 3. Se, mesmo com o raio ampliado, não houver elegíveis, registra "nenhum receptor elegível" no resumo e retorna ao passo 8 do fluxo principal. |

## Fluxo de Exceção 2a. Lote não está mais disponível

| Ações do Ator | Ações do Sistema |
|---|---|
| | 1. Constata que o lote não está com status `DISPONIVEL` ou que a data de validade já passou no momento do envio. |
| | 2. Encerra sem enviar notificações, registrando o motivo no resumo. |

## Fluxo de Exceção 6a. Falha no envio a um Receptor

| Ações do Ator | Ações do Sistema |
|---|---|
| | 1. O Sistema de Notificação recusa ou não consegue entregar a mensagem a um dos Receptores. |
| | 2. Registra a notificação com situação `FALHA` e o motivo, sem interromper os envios aos demais elegíveis e sem desfazer a publicação do lote. |
| | 3. Contabiliza a falha no resumo devolvido ao UC02 e retorna ao passo 6 para o próximo Receptor. |

## Regras adotadas (lacunas do CONTEXTO_PROJETO.md)

- **Lacuna 11 (fórmula do raio):** adotada a tabela do passo 3, inspirada no README ("alimentos que vencem em 2 horas só notificam ONGs a 5 km; em 12 horas, a 20 km"). Está centralizada em `MotorRoteamento.raioKm` para ser ajustada sem tocar no restante.
- **Geolocalização:** o cálculo de distância usa a fórmula de Haversine sobre latitude/longitude do endereço de retirada do lote e do endereço da instituição. A geocodificação automática de endereços (lacuna 14, serviço de mapas) não está implementada: as coordenadas são informadas no cadastro (UC01) e, no caso do endereço alternativo, no cadastro do lote (UC02-7a).
- **Aprovação (RF06):** só instituições com cadastro aprovado recebem notificações, pois só elas podem reservar (UC04).
- **Canal de envio (RF07):** o Sistema de Notificação é a interface `Notificador` (a mesma usada pelo e-mail do UC01); a implementação atual registra o envio no log do servidor e a notificação fica gravada no banco para o Receptor consultar na tela "Notificações".

## Rastreabilidade para o código

| Passo | Classe / método |
|---|---|
| 1, 8 | `LoteAlimentoService.cadastrar` → `NotificacaoService.notificarAlimentosProximos` → `NotificacaoService.Resultado` |
| 2-3, 4a | `MotorRoteamento.raioKm`, `MotorRoteamento.ampliar` |
| 4-5 | `MotorRoteamento.elegiveis` (sobre `UsuarioRepository.listarInstituicoesAtivas`) |
| 6-7, 6a | `NotificacaoService.enviar` (grava `Notificacao`; `Notificador` é o ator secundário) |
| 3a, 2a | `NotificacaoService.notificarAlimentosProximos` (motivos `SEM_LOCALIZACAO` e `LOTE_INDISPONIVEL`) |
| Consulta pelo Receptor | `NotificacaoService.listar` / `GET /api/notificacoes` |
| Testes | `MotorRoteamentoTest` (passos 2-5), `NotificacaoServiceTest` (fluxo principal, 3a, 4a, 2a, 6a) |
