# Territory Predictive Insights: chuva e risco de alagamento

**Global Solution 2º/2026 · Programação Orientada a Objetos (Turma de Fevereiro)**
**Tema:** CitizenTech · **Recorte escolhido:** 🌊 Clima local (chuva intensa e risco de alagamento em pontos vulneráveis)

| Integrante | RM |
|---|---|
| Gregory Debom Ferreira | 562346 |
| Luiz Henrique Barbosa Dias | 562399 |

Sistema de console em Java que lê sensores (simulados) de um território, valida e grava cada medição em banco de dados, prevê o comportamento dos próximos períodos com duas estratégias e emite alertas (ATENÇÃO, ALERTA, CRÍTICO) explicando o motivo de cada um.

---

## 1. Como executar

**Pré-requisito:** Java **17 ou mais novo** (`java -version`). Não precisa instalar Maven nem banco de dados: o projeto traz o **Maven Wrapper** (`mvnw`) e usa o **H2** embutido.

Todos os comandos são executados **na pasta do projeto** (onde está o `pom.xml`).

```bash
# Windows (cmd ou PowerShell)
.\mvnw.cmd clean package          # compila, roda os 28 testes e gera o JAR
java -jar target\territory-predictive-insights.jar

# Linux / macOS
./mvnw clean package
java -jar target/territory-predictive-insights.jar
```

- Só os testes: `.\mvnw.cmd test`
- Na primeira execução o wrapper baixa o Maven e as dependências (precisa de internet uma vez).

### Banco de dados
- **H2 em arquivo**: `jdbc:h2:file:./dados/territorio` (configurado em `src/main/resources/config.properties`).
- As tabelas são criadas automaticamente pelo `schema.sql` na primeira execução (`CREATE TABLE IF NOT EXISTS`).
- O histórico fica na pasta `dados/` e **sobrevive ao reiniciar o programa**. Para começar do zero, apague essa pasta.
- Para usar outro banco/arquivo sem recompilar: `java -Ddb.url=jdbc:h2:file:./outro -jar target\territory-predictive-insights.jar`.
- O H2 em arquivo aceita **um programa aberto por vez**: feche o sistema antes de abrir outra janela.

### Roteiro rápido para ver tudo funcionando
1. **Opção 1** (verificar sensores) com **12 ciclos**: grava o histórico. Algumas leituras falham (`FonteIndisponivelException`) ou são descartadas (`DadosInvalidosException`) de propósito, simulando defeitos reais.
2. **Opção 2**: roda as duas previsões sobre o histórico do banco e emite alertas.
3. **Opção 3**: mostra o histórico por sensor e os alertas emitidos, e exporta CSV (bônus).
4. **Opção 4 → 4**: altera limiares (código de acesso: `fiap2026`). Ex.: chuva 5 / 8 / 12 e depois a opção 2 de novo, para ver mais alertas.
5. **Opção 5**: status completo.
6. Saia (0), abra de novo e use a opção 3: o histórico continua lá.

---

## 2. Público beneficiado e decisão apoiada

**Quem sofre o problema:** moradores de baixadas próximas a córregos e de encostas em bairros periféricos, onde chuvas fortes causam alagamento de ruas e casas, enxurradas e deslizamentos. São, na maior parte, famílias com menos recursos para se recuperar das perdas.

**Quem usa o sistema:** a coordenação de uma associação de bairro ou de um núcleo comunitário de Defesa Civil (NUPDEC), que acompanha os pontos vulneráveis do território.

**Decisão apoiada:** *"Precisamos agir agora? Com que intensidade?"*: verificar bueiros e bocas de lobo (ATENÇÃO), avisar moradores e preparar abrigos (ALERTA) ou acionar a Defesa Civil e orientar a saída preventiva (CRÍTICO). O sistema mostra o nível, o sensor, o valor previsto, o limite estourado e a recomendação, mas **quem decide é a pessoa responsável**.

O território é descrito só por regiões, equipamentos e números agregados (população exposta, domicílios em área de risco). **Não há nenhum dado pessoal.** Nomes de lugares são fictícios.

---

## 3. Arquitetura e conceitos de POO

```
src/main/java/
├── dominio/       ComponenteTerritorial (abstrata), PontoMonitoramento, SensorSimulado (abstrata),
│                  Sensor (interface), Pluviometro, SensorNivelCorrego, SensorUmidadeSolo,
│                  DadosTerritorio, Limiares, TipoSensor, StatusComponente,
│                  EstrategiaPrevisao (abstrata), PrevisaoMediaMovel, PrevisaoComparacao,
│                  ResultadoPrevisao, Medicao, Alerta, NivelAlerta
├── excecao/       DadosInvalidosException, FonteIndisponivelException
├── persistencia/  ConexaoBanco, MedicaoDAO, AlertaDAO           (JDBC + PreparedStatement)
├── servico/       ServicoMonitoramento, FabricaPrevisao (Factory), ConfiguracaoSistema (Singleton),
│                  RelatorioBuilder + Relatorio (Builder), ExportadorCsv, ResultadoLeitura, RodadaPrevisao
└── SistemaMonitoramento.java   (menu principal com Scanner)
src/main/resources/  config.properties, schema.sql
src/test/java/       MedicaoTest, PrevisaoMediaMovelTest, PrevisaoComparacaoTest, DadosTerritorioTest,
                     FabricaPrevisaoTest, ServicoMonitoramentoTest (Mockito), MedicaoDAOIntegracaoTest (H2)
```

### 3.1 Domínio orientado a objetos
| Requisito | Onde está |
|---|---|
| Classe abstrata `ComponenteTerritorial` (id, nome, região, status) com `verificarStatus()` abstrato | `dominio/ComponenteTerritorial.java` |
| Filhas concretas | `PontoMonitoramento` (status pelo nº de sensores funcionando) e, via `SensorSimulado`, os 3 sensores (status por manutenção ou falhas seguidas) |
| Interface `Sensor` (ler valor, verificar funcionamento, retornar tipo) com 3 implementações | `Pluviometro` (mm/h), `SensorNivelCorrego` (m), `SensorUmidadeSolo` (%) |
| `DadosTerritorio` com atributos privados e acesso controlado | setters validados e `autorizarAlteracaoDeLimiares(codigo)`: alterar limiares exige código de acesso |
| `EstrategiaPrevisao` abstrata com 2 filhos que sobrescrevem `prever()` e usam `super()` | `PrevisaoMediaMovel` e `PrevisaoComparacao` chamam `super(...)` no construtor e `super.prever(historico)`, que faz a validação comum, antes do próprio cálculo |
| Escolha da estratégia sem `if tipo ==` / `instanceof` | `FabricaPrevisao` (mapa nome → construtor) e polimorfismo no laço do serviço |

**As duas estratégias se comportam de forma diferente:**
- **Média móvel:** média das últimas N (=5) leituras. Mostra o *patamar* atual e suaviza picos isolados.
- **Comparação de períodos:** compara a média das últimas N leituras com a das N anteriores e projeta a mesma variação. Reage à *tendência* (subindo ou descendo) e precisa de 2N leituras.

### 3.2 Exceções customizadas
- `DadosInvalidosException`: medição fora da faixa física (*"medição de chuva negativa (-3,00 mm/h)"*), população negativa, limiar fora de ordem, código de acesso errado, sensor inexistente, estratégia desconhecida.
- `FonteIndisponivelException`: sensor sem resposta ou em manutenção, e falha de conexão ou consulta ao banco.
- São **lançadas** nos pontos de validação: construtor de `Medicao`, setters de `DadosTerritorio`, construtor de `Limiares`, `SensorSimulado.lerValor()`, DAOs e `FabricaPrevisao`.
- São **capturadas** no serviço (uma leitura com defeito não derruba as outras) e no menu, sempre com mensagem clara. **Nenhum `catch` vazio:** todo erro capturado é exibido ou convertido em um resultado explicado.

### 3.3 Persistência (caminho JDBC)
- `MedicaoDAO` e `AlertaDAO` usam **somente `PreparedStatement` com parâmetros `?`**. Não há SQL montado por concatenação de dados.
- Todos os `Connection`, `PreparedStatement` e `ResultSet` são fechados com **try-with-resources**.
- Cada medição válida e cada alerta emitido são gravados. O menu lista o histórico por sensor e os alertas emitidos.
- As previsões leem as últimas leituras **do banco** (`buscarUltimasPorSensor`), e não de uma lista em memória.

### 3.4 Padrões criacionais (três aplicados) e justificativa
| Padrão | Classe | Por que ali (e não em outro lugar) |
|---|---|---|
| **Factory** | `FabricaPrevisao` | A estratégia vem de um *texto* de configuração (`previsao.estrategias=MEDIA_MOVEL,COMPARACAO`). Sem a fábrica, transformar esse texto em objeto exigiria um `if/switch` de tipos no serviço e no menu. Com o catálogo (mapa nome → construtor), nova estratégia = uma linha `registrar(...)`, sem mudar o serviço. Não faz sentido usar Factory para `Medicao` ou `Alerta`: eles têm um único tipo concreto e o construtor já valida tudo. |
| **Singleton** | `ConfiguracaoSistema` | Os limiares precisam ser **uma única verdade** para o sistema inteiro: quando a coordenação muda um limiar no menu, o serviço de alertas precisa enxergar a mudança na hora. Duas instâncias poderiam divergir. Construtor privado e acesso por `getInstance()`. Não usamos Singleton nos DAOs porque eles recebem a conexão por construtor, e isso permite trocar por mocks nos testes. |
| **Builder** | `RelatorioBuilder` → `Relatorio` | O "status completo" tem muitas partes opcionais (território, pontos, sensores, limiares, situação prevista, alertas). Um construtor com 7 parâmetros seria ilegível. O builder monta passo a passo (`.comTerritorio(...).comLimiares(...).comAlertasRecentes(...)`) e valida no `construir()`. |

O código **chama** os padrões em vez de duplicar a criação: o serviço obtém as estratégias com `fabrica.criarConfiguradas(config)`, os limiares sempre vêm de `ConfiguracaoSistema.getInstance()` e o relatório só é montado pelo builder.

### 3.5 Testes unitários: 28 testes, todos no padrão AAA
| Classe de teste | O que verifica |
|---|---|
| `MedicaoTest` (5) | medição válida; chuva negativa, nível acima da régua, umidade > 100% e sensor vazio lançam `DadosInvalidosException` (**`assertThrows`**) |
| `PrevisaoMediaMovelTest` (4) | média das últimas 5 com valores exatos; silêncio com poucas leituras; filha sobrescreve a mãe; histórico misturado é rejeitado |
| `PrevisaoComparacaoTest` (3) | projeção de tendência (10 → 20 → **30**); não projeta chuva negativa; exige dois períodos |
| `DadosTerritorioTest` (4) | população negativa; código de acesso; limiares fora de ordem; classificação do nível |
| `FabricaPrevisaoTest` (3) | Factory por nome; nome desconhecido; Singleton com instância única |
| `ServicoMonitoramentoTest` (7) | **Mockito** mockando `MedicaoDAO`, `AlertaDAO` e `Sensor`: alerta CRÍTICO gravado com o limite certo, nenhum alerta abaixo do limiar, silêncio com < 5 leituras, leitura inválida e sensor com falha não vão para o banco, e limiares só mudam com o código certo |
| `MedicaoDAOIntegracaoTest` (2) | DAOs contra H2 **em memória**: SQL, ordem cronológica e mapeamento |

Os testes usam valores exatos: se o cálculo da média, da projeção ou da classificação mudar, eles falham.

### 3.6 Bônus: exportar CSV
Menu **3 → 3** exporta o histórico consultado de um sensor para `exportacoes/historico_<SENSOR>_<data>.csv` (separador `;`, vírgula decimal e UTF-8 com BOM, para abrir direto no Excel em português).

---

## 4. Decisões da equipe
- **JDBC em vez de Spring Boot:** o desafio é de console. JDBC deixa explícito o que acontece no banco (SQL, `PreparedStatement`, fechamento de recursos), que é justamente o que a GS avalia, sem o peso de subir um contexto Spring.
- **H2 em arquivo** (e não em memória) para cumprir *"os dados sobrevivem ao reiniciar"* sem pedir instalação de banco ao professor. Nos testes de integração usamos H2 em memória, com um banco novo por teste.
- **Exceções verificadas (checked):** o compilador obriga a tratar `DadosInvalidosException` e `FonteIndisponivelException`, então um dado inválido não "escapa" até o banco.
- **`Medicao` se valida no construtor:** não existe objeto `Medicao` com valor impossível.
- **`Limiares` imutável:** alterar limiar = criar um novo objeto validado (ATENÇÃO < ALERTA < CRÍTICO).
- **Um alerta por sensor por rodada:** as duas estratégias podem acusar o mesmo sensor; gravamos só o mais grave, para não gerar ruído.
- **Simulação realista:** chuva com "pancadas" que se dissipam, nível do córrego e umidade com passeio aleatório em torno do normal, e pequenas chances de falha de comunicação (5%) e de leitura absurda (4%) para exercitar as exceções.
- **Limiares de chuva** (10 / 30 / 50 mm/h) inspirados nas faixas usuais de chuva forte, muito forte e extrema. São configuráveis em `config.properties` e pelo menu.
- **Java 17 como alvo:** compila e roda em qualquer JDK 17+.

---

## 5. Reflexão crítica

**Quando o sistema deve se calar?** Previsão com pouco histórico é chute. Por isso a classe base `EstrategiaPrevisao` exige um mínimo de leituras: 5 para a média móvel e 10 para a comparação, que precisa de dois períodos. Abaixo disso o resultado sai marcado como "não confiável" e **nenhum alerta é emitido**, mesmo que as poucas leituras sejam altíssimas. Isso é testado em `deveFicarEmSilencioComPoucasLeituras`. O sistema também deveria se calar, ou ao menos avisar, quando um sensor está em manutenção ou falhando seguidamente: o status passa para DEGRADADO/INOPERANTE e não há leitura nova para prever. Ainda assim, "calar" tem custo: um sistema calado na hora errada dá falsa sensação de segurança. Por isso o silêncio é **explicado** ("há 3 leituras e esta estratégia precisa de 5"), e não escondido.

**O que os dados não representam?** Um pluviômetro mede a chuva *em um ponto*; a chuva varia muito de uma rua para outra, e um único sensor por região diz pouco sobre uma viela específica. O alagamento também depende de coisas que o sistema não mede: bueiros entupidos por lixo, obras, maré, a chuva que cai rio acima em outro bairro, a impermeabilização do solo e a situação das casas. Média e tendência das últimas leituras não "sabem" que vem uma frente fria. Além disso, os dados aqui são **simulados**. E o número de "população exposta" é um agregado: não diz quem tem mobilidade reduzida, crianças pequenas ou nenhum lugar para onde ir.

**Por que o alerta apoia a decisão em vez de decidir?** Porque o custo dos erros é assimétrico e humano. Retirar famílias de casa por um falso alarme gera desgaste e descrédito ("o sistema sempre grita"); ignorar um alerta real pode custar vidas. Quem conhece o território sabe se aquele bueiro foi limpo ontem, se a encosta já cedeu antes, quem precisa de ajuda para sair. O sistema entrega **evidência organizada** (sensor, valor previsto, limite estourado, estratégia e recomendação), e a pessoa responsável confirma em campo e decide. A responsabilidade e o vínculo de confiança com os moradores continuam humanos.

---

## 6. Uso de IA

A equipe utilizou o **Claude Code (Anthropic)**, um assistente de IA, no VS Code:
- **Onde foi usado:** geração da estrutura inicial do projeto Maven, das classes de domínio, persistência e serviço, dos testes, do menu e do rascunho deste README, a partir do enunciado da GS.
- **Como revisamos:** *(a equipe deve completar com o que fez de fato)*. Por exemplo: leitura de cada classe para entender o que faz, execução dos testes (`mvnw test`) e do menu completo, alteração de valores para ver testes falhando, discussão das decisões de projeto e ajustes no código e no texto.

Todos os integrantes devem ser capazes de explicar qualquer trecho do código na conversa de correção.

---

## 7. Divisão de tarefas

*(Sugestão inicial: a equipe deve ajustar para refletir o que cada um fez.)*

| Integrante | Responsabilidades |
|---|---|
| Gregory Debom Ferreira (RM 562346) | Domínio e exceções (`ComponenteTerritorial`, sensores, `DadosTerritorio`, `Medicao`, `Limiares`, exceções customizadas), menu de console |
| Luiz Henrique Barbosa Dias (RM 562399) | Persistência JDBC (DAOs, `ConexaoBanco`, `schema.sql`), previsões e alertas (estratégias, `ServicoMonitoramento`, padrões criacionais), testes e README |
