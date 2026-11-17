package servico;

import dominio.Alerta;
import dominio.DadosTerritorio;
import dominio.EstrategiaPrevisao;
import dominio.Limiares;
import dominio.Medicao;
import dominio.NivelAlerta;
import dominio.PontoMonitoramento;
import dominio.ResultadoPrevisao;
import dominio.Sensor;
import dominio.TipoSensor;
import excecao.DadosInvalidosException;
import excecao.FonteIndisponivelException;
import persistencia.AlertaDAO;
import persistencia.MedicaoDAO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Regra de negócio do monitoramento: ler sensores, validar e gravar medições,
 * rodar as previsões sobre o histórico do banco e emitir alertas.
 *
 * Os DAOs chegam pelo construtor (injeção de dependência): nos testes eles são
 * substituídos por mocks do Mockito e a lógica roda sem banco.
 */
public class ServicoMonitoramento {

    private final MedicaoDAO medicaoDAO;
    private final AlertaDAO alertaDAO;
    private final List<PontoMonitoramento> pontos;
    private final List<Sensor> sensores;
    private final DadosTerritorio territorio;
    private final ConfiguracaoSistema configuracao;
    private final List<EstrategiaPrevisao> estrategias;

    public ServicoMonitoramento(MedicaoDAO medicaoDAO, AlertaDAO alertaDAO, List<PontoMonitoramento> pontos,
                                DadosTerritorio territorio, ConfiguracaoSistema configuracao,
                                FabricaPrevisao fabrica) throws DadosInvalidosException {
        this.medicaoDAO = Objects.requireNonNull(medicaoDAO, "medicaoDAO");
        this.alertaDAO = Objects.requireNonNull(alertaDAO, "alertaDAO");
        this.pontos = List.copyOf(pontos);
        this.sensores = pontos.stream().flatMap(p -> p.getSensores().stream()).toList();
        this.territorio = Objects.requireNonNull(territorio, "territorio");
        this.configuracao = Objects.requireNonNull(configuracao, "configuracao");
        this.estrategias = fabrica.criarConfiguradas(configuracao); // estratégias criadas pela Factory
    }

    // ------------------------------------------------------------------ leituras

    /** Lê todos os sensores uma vez. Cada leitura válida é gravada no banco. */
    public List<ResultadoLeitura> verificarSensores() {
        List<ResultadoLeitura> resultados = new ArrayList<>();
        for (Sensor sensor : sensores) {
            resultados.add(lerESalvar(sensor));
        }
        return resultados;
    }

    /**
     * Lê um sensor, valida o valor (criando a Medicao) e grava no banco.
     * Os erros não interrompem os demais sensores: viram um resultado com a explicação.
     */
    public ResultadoLeitura lerESalvar(Sensor sensor) {
        try {
            double valor = sensor.lerValor();
            Medicao medicao = Medicao.nova(sensor.getCodigo(), sensor.getTipo(), valor); // valida a faixa
            return ResultadoLeitura.gravada(medicaoDAO.salvar(medicao));
        } catch (DadosInvalidosException e) {
            return ResultadoLeitura.invalida(sensor, e.getMessage());
        } catch (FonteIndisponivelException e) {
            return ResultadoLeitura.falha(sensor, e.getMessage());
        }
    }

    // ------------------------------------------------------------------ previsões e alertas

    /**
     * Roda todas as estratégias sobre o histórico de cada sensor e grava os alertas.
     * Para não duplicar avisos, cada sensor gera no máximo um alerta por rodada: o mais grave.
     */
    public RodadaPrevisao rodarPrevisoes() throws FonteIndisponivelException {
        List<ResultadoPrevisao> previsoes = new ArrayList<>();
        List<Alerta> alertas = new ArrayList<>();
        for (Sensor sensor : sensores) {
            List<ResultadoPrevisao> doSensor = preverSensor(sensor);
            previsoes.addAll(doSensor);
            Optional<Alerta> maisGrave = doSensor.stream()
                    .map(this::avaliarAlerta)
                    .flatMap(Optional::stream)
                    .max(Comparator.comparing(Alerta::getNivel));
            if (maisGrave.isPresent()) {
                alertas.add(alertaDAO.salvar(maisGrave.get()));
            }
        }
        return new RodadaPrevisao(previsoes, alertas);
    }

    /** Aplica cada estratégia (polimorfismo: cada uma tem seu prever()) ao histórico do sensor. */
    public List<ResultadoPrevisao> preverSensor(Sensor sensor) throws FonteIndisponivelException {
        int leiturasNecessarias = estrategias.stream().mapToInt(EstrategiaPrevisao::getMinimoLeituras).max().orElse(1);
        List<Medicao> historico = medicaoDAO.buscarUltimasPorSensor(sensor.getCodigo(), leiturasNecessarias);
        List<ResultadoPrevisao> resultados = new ArrayList<>();
        for (EstrategiaPrevisao estrategia : estrategias) {
            ResultadoPrevisao resultado = historico.isEmpty()
                    ? ResultadoPrevisao.insuficiente(sensor.getCodigo(), sensor.getTipo(), estrategia.getNome(), 0,
                    estrategia.getMinimoLeituras())
                    : estrategia.prever(historico);
            resultados.add(resultado);
        }
        return resultados;
    }

    /**
     * Compara a previsão com os limiares do ConfiguracaoSistema. Não consulta o banco.
     * Previsão não confiável (poucos dados) nunca gera alerta: o sistema se cala.
     */
    public Optional<Alerta> avaliarAlerta(ResultadoPrevisao previsao) {
        if (!previsao.isConfiavel()) {
            return Optional.empty();
        }
        TipoSensor tipo = previsao.getTipo();
        Limiares limiares = configuracao.getLimiares(tipo);
        double valor = previsao.getValorPrevisto();
        return limiares.classificar(valor).map(nivel -> criarAlerta(previsao, nivel, limiares.limiteDe(nivel)));
    }

    private Alerta criarAlerta(ResultadoPrevisao previsao, NivelAlerta nivel, double limite) {
        TipoSensor tipo = previsao.getTipo();
        String local = sensores.stream()
                .filter(s -> s.getCodigo().equals(previsao.getSensorCodigo()))
                .map(s -> " em " + s.getLocalizacao())
                .findFirst().orElse("");
        String mensagem = String.format(
                "%s (%s%s): %s prevista de %s pela estratégia '%s' atingiu o limite de %s (%s). Recomendação: %s",
                previsao.getSensorCodigo(), tipo.getNomeExibicao(), local, tipo.getGrandeza(),
                tipo.formatar(previsao.getValorPrevisto()), previsao.getEstrategia(), nivel.getRotulo(),
                tipo.formatar(limite), nivel.getRecomendacao());
        return new Alerta(null, previsao.getSensorCodigo(), tipo, nivel, previsao.getValorPrevisto(), limite,
                previsao.getEstrategia(), mensagem, LocalDateTime.now());
    }

    // ------------------------------------------------------------------ histórico

    public List<Medicao> buscarHistoricoPorSensor(String sensorCodigo)
            throws FonteIndisponivelException, DadosInvalidosException {
        return medicaoDAO.buscarHistoricoPorSensor(buscarSensor(sensorCodigo).getCodigo());
    }

    public List<Alerta> listarAlertasEmitidos() throws FonteIndisponivelException {
        return alertaDAO.listarTodos();
    }

    public Sensor buscarSensor(String sensorCodigo) throws DadosInvalidosException {
        return sensores.stream()
                .filter(s -> s.getCodigo().equalsIgnoreCase(sensorCodigo == null ? "" : sensorCodigo.trim()))
                .findFirst()
                .orElseThrow(() -> new DadosInvalidosException("sensor '" + sensorCodigo + "' não existe neste território."));
    }

    // ------------------------------------------------------------------ dados do território

    /**
     * Altera os limiares de um tipo de sensor. Exige o código de acesso do território
     * e limites coerentes (ATENÇÃO < ALERTA < CRÍTICO).
     */
    public Limiares alterarLimiares(String codigoAcesso, TipoSensor tipo, double atencao, double alerta,
                                    double critico) throws DadosInvalidosException {
        territorio.autorizarAlteracaoDeLimiares(codigoAcesso);
        Limiares novos = new Limiares(atencao, alerta, critico);
        configuracao.definirLimiares(tipo, novos);
        return novos;
    }

    // ------------------------------------------------------------------ status completo

    /** Relatório do território inteiro, montado com o RelatorioBuilder. */
    public Relatorio gerarStatusCompleto() throws FonteIndisponivelException {
        List<String> linhasSensores = new ArrayList<>();
        List<String> linhasSituacao = new ArrayList<>();
        for (Sensor sensor : sensores) {
            int leituras = medicaoDAO.contarPorSensor(sensor.getCodigo());
            linhasSensores.add(String.format("%-7s %-16s %-28s funcionando: %-3s | leituras no banco: %d",
                    sensor.getCodigo(), sensor.getTipo().getNomeExibicao(), sensor.getLocalizacao(),
                    sensor.verificarFuncionamento() ? "sim" : "NÃO", leituras));
            linhasSituacao.add(descreverSituacaoPrevista(sensor));
        }
        return RelatorioBuilder.novo()
                .comTitulo("STATUS COMPLETO - " + territorio.getNomeTerritorio())
                .comTerritorio(territorio)
                .comPontosMonitorados(pontos)
                .comSecao("Sensores", linhasSensores)
                .comLimiares(configuracao.getTodosLimiares())
                .comSecao("Situação prevista agora (consulta, não grava alerta)", linhasSituacao)
                .comAlertasRecentes(alertaDAO.listarTodos(), 5)
                .construir();
    }

    private String descreverSituacaoPrevista(Sensor sensor) throws FonteIndisponivelException {
        List<ResultadoPrevisao> previsoes = preverSensor(sensor);
        Optional<ResultadoPrevisao> confiavel = previsoes.stream().filter(ResultadoPrevisao::isConfiavel).findFirst();
        if (confiavel.isEmpty()) {
            return sensor.getCodigo() + ": sem previsão (histórico insuficiente) - sistema em silêncio.";
        }
        Optional<NivelAlerta> pior = previsoes.stream()
                .map(this::avaliarAlerta)
                .flatMap(Optional::stream)
                .map(Alerta::getNivel)
                .max(Comparator.naturalOrder());
        String situacao = pior.map(n -> "nível " + n.getRotulo()).orElse("dentro do normal");
        ResultadoPrevisao r = confiavel.get();
        return String.format("%s: %s pela %s -> %s", sensor.getCodigo(),
                r.getTipo().formatar(r.getValorPrevisto()), r.getEstrategia().toLowerCase(), situacao);
    }

    // ------------------------------------------------------------------ getters

    public List<Sensor> getSensores() {
        return sensores;
    }

    public List<PontoMonitoramento> getPontos() {
        return pontos;
    }

    public List<EstrategiaPrevisao> getEstrategias() {
        return estrategias;
    }

    public DadosTerritorio getTerritorio() {
        return territorio;
    }
}
