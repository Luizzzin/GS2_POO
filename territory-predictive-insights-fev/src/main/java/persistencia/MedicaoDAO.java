package persistencia;

import dominio.Medicao;
import dominio.TipoSensor;
import excecao.DadosInvalidosException;
import excecao.FonteIndisponivelException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Acesso à tabela MEDICAO. Todo SQL usa PreparedStatement com parâmetros "?"
 * (nunca concatenação de String) e todos os recursos JDBC são fechados com
 * try-with-resources.
 */
public class MedicaoDAO {

    private static final String SQL_INSERIR =
            "INSERT INTO medicao (sensor_codigo, tipo, valor, data_hora) VALUES (?, ?, ?, ?)";
    private static final String SQL_HISTORICO_POR_SENSOR =
            "SELECT id, sensor_codigo, tipo, valor, data_hora FROM medicao "
                    + "WHERE sensor_codigo = ? ORDER BY data_hora, id";
    private static final String SQL_ULTIMAS_POR_SENSOR =
            "SELECT id, sensor_codigo, tipo, valor, data_hora FROM medicao "
                    + "WHERE sensor_codigo = ? ORDER BY data_hora DESC, id DESC LIMIT ?";
    private static final String SQL_CONTAR_POR_SENSOR =
            "SELECT COUNT(*) FROM medicao WHERE sensor_codigo = ?";

    private final ConexaoBanco conexaoBanco;

    public MedicaoDAO(ConexaoBanco conexaoBanco) {
        this.conexaoBanco = conexaoBanco;
    }

    /** Grava a medição e devolve uma cópia com o id gerado pelo banco. */
    public Medicao salvar(Medicao medicao) throws FonteIndisponivelException {
        try (Connection conexao = conexaoBanco.abrirConexao();
             PreparedStatement comando = conexao.prepareStatement(SQL_INSERIR, Statement.RETURN_GENERATED_KEYS)) {
            comando.setString(1, medicao.getSensorCodigo());
            comando.setString(2, medicao.getTipo().name());
            comando.setDouble(3, medicao.getValor());
            comando.setObject(4, medicao.getDataHora());
            comando.executeUpdate();
            try (ResultSet chaves = comando.getGeneratedKeys()) {
                if (!chaves.next()) {
                    throw new FonteIndisponivelException("O banco não devolveu o id da medição gravada.");
                }
                return medicao.comId(chaves.getLong(1));
            }
        } catch (SQLException e) {
            throw new FonteIndisponivelException("Erro ao gravar a medição do sensor "
                    + medicao.getSensorCodigo() + ": " + e.getMessage(), e);
        }
    }

    /** Todo o histórico de um sensor, da leitura mais antiga para a mais recente. */
    public List<Medicao> buscarHistoricoPorSensor(String sensorCodigo) throws FonteIndisponivelException {
        try (Connection conexao = conexaoBanco.abrirConexao();
             PreparedStatement comando = conexao.prepareStatement(SQL_HISTORICO_POR_SENSOR)) {
            comando.setString(1, sensorCodigo);
            return lerLista(comando);
        } catch (SQLException e) {
            throw new FonteIndisponivelException("Erro ao consultar o histórico do sensor " + sensorCodigo
                    + ": " + e.getMessage(), e);
        }
    }

    /**
     * As {@code quantidade} leituras mais recentes de um sensor, em ordem cronológica
     * (da mais antiga para a mais recente), prontas para as estratégias de previsão.
     */
    public List<Medicao> buscarUltimasPorSensor(String sensorCodigo, int quantidade)
            throws FonteIndisponivelException {
        try (Connection conexao = conexaoBanco.abrirConexao();
             PreparedStatement comando = conexao.prepareStatement(SQL_ULTIMAS_POR_SENSOR)) {
            comando.setString(1, sensorCodigo);
            comando.setInt(2, quantidade);
            List<Medicao> maisRecentesPrimeiro = lerLista(comando);
            Collections.reverse(maisRecentesPrimeiro);
            return maisRecentesPrimeiro;
        } catch (SQLException e) {
            throw new FonteIndisponivelException("Erro ao consultar as últimas leituras do sensor " + sensorCodigo
                    + ": " + e.getMessage(), e);
        }
    }

    public int contarPorSensor(String sensorCodigo) throws FonteIndisponivelException {
        try (Connection conexao = conexaoBanco.abrirConexao();
             PreparedStatement comando = conexao.prepareStatement(SQL_CONTAR_POR_SENSOR)) {
            comando.setString(1, sensorCodigo);
            try (ResultSet resultado = comando.executeQuery()) {
                resultado.next();
                return resultado.getInt(1);
            }
        } catch (SQLException e) {
            throw new FonteIndisponivelException("Erro ao contar as leituras do sensor " + sensorCodigo
                    + ": " + e.getMessage(), e);
        }
    }

    private List<Medicao> lerLista(PreparedStatement comando) throws SQLException, FonteIndisponivelException {
        List<Medicao> medicoes = new ArrayList<>();
        try (ResultSet resultado = comando.executeQuery()) {
            while (resultado.next()) {
                medicoes.add(mapear(resultado));
            }
        }
        return medicoes;
    }

    private Medicao mapear(ResultSet linha) throws SQLException, FonteIndisponivelException {
        long id = linha.getLong("id");
        try {
            return new Medicao(id, linha.getString("sensor_codigo"), TipoSensor.valueOf(linha.getString("tipo")),
                    linha.getDouble("valor"), linha.getObject("data_hora", LocalDateTime.class));
        } catch (DadosInvalidosException | IllegalArgumentException e) {
            throw new FonteIndisponivelException("Registro de medição corrompido no banco (id " + id + "): "
                    + e.getMessage(), e);
        }
    }
}
