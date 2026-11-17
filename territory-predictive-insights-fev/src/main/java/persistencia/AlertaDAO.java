package persistencia;

import dominio.Alerta;
import dominio.NivelAlerta;
import dominio.TipoSensor;
import excecao.FonteIndisponivelException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Acesso à tabela ALERTA (PreparedStatement + try-with-resources). */
public class AlertaDAO {

    private static final String SQL_INSERIR = "INSERT INTO alerta (sensor_codigo, tipo, nivel, valor_previsto, "
            + "limite, estrategia, mensagem, data_hora) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String SQL_LISTAR_TODOS = "SELECT id, sensor_codigo, tipo, nivel, valor_previsto, limite, "
            + "estrategia, mensagem, data_hora FROM alerta ORDER BY data_hora DESC, id DESC";

    private final ConexaoBanco conexaoBanco;

    public AlertaDAO(ConexaoBanco conexaoBanco) {
        this.conexaoBanco = conexaoBanco;
    }

    /** Grava o alerta e devolve uma cópia com o id gerado pelo banco. */
    public Alerta salvar(Alerta alerta) throws FonteIndisponivelException {
        try (Connection conexao = conexaoBanco.abrirConexao();
             PreparedStatement comando = conexao.prepareStatement(SQL_INSERIR, Statement.RETURN_GENERATED_KEYS)) {
            comando.setString(1, alerta.getSensorCodigo());
            comando.setString(2, alerta.getTipo().name());
            comando.setString(3, alerta.getNivel().name());
            comando.setDouble(4, alerta.getValorPrevisto());
            comando.setDouble(5, alerta.getLimite());
            comando.setString(6, alerta.getEstrategia());
            comando.setString(7, alerta.getMensagem());
            comando.setObject(8, alerta.getDataHora());
            comando.executeUpdate();
            try (ResultSet chaves = comando.getGeneratedKeys()) {
                if (!chaves.next()) {
                    throw new FonteIndisponivelException("O banco não devolveu o id do alerta gravado.");
                }
                return alerta.comId(chaves.getLong(1));
            }
        } catch (SQLException e) {
            throw new FonteIndisponivelException("Erro ao gravar o alerta do sensor " + alerta.getSensorCodigo()
                    + ": " + e.getMessage(), e);
        }
    }

    /** Todos os alertas já emitidos, do mais recente para o mais antigo. */
    public List<Alerta> listarTodos() throws FonteIndisponivelException {
        try (Connection conexao = conexaoBanco.abrirConexao();
             PreparedStatement comando = conexao.prepareStatement(SQL_LISTAR_TODOS);
             ResultSet resultado = comando.executeQuery()) {
            List<Alerta> alertas = new ArrayList<>();
            while (resultado.next()) {
                alertas.add(mapear(resultado));
            }
            return alertas;
        } catch (SQLException e) {
            throw new FonteIndisponivelException("Erro ao consultar os alertas emitidos: " + e.getMessage(), e);
        }
    }

    private Alerta mapear(ResultSet linha) throws SQLException, FonteIndisponivelException {
        long id = linha.getLong("id");
        try {
            return new Alerta(id, linha.getString("sensor_codigo"), TipoSensor.valueOf(linha.getString("tipo")),
                    NivelAlerta.valueOf(linha.getString("nivel")), linha.getDouble("valor_previsto"),
                    linha.getDouble("limite"), linha.getString("estrategia"), linha.getString("mensagem"),
                    linha.getObject("data_hora", LocalDateTime.class));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new FonteIndisponivelException("Registro de alerta corrompido no banco (id " + id + "): "
                    + e.getMessage(), e);
        }
    }
}
