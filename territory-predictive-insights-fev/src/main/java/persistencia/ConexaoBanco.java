package persistencia;

import excecao.FonteIndisponivelException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Abre conexões JDBC com o banco H2 e cria as tabelas na primeira execução.
 *
 * Com a URL padrão ({@code jdbc:h2:file:./dados/territorio}) o banco é gravado em
 * arquivo na pasta {@code dados/}: o histórico sobrevive ao reiniciar o programa.
 */
public class ConexaoBanco {

    private static final String ARQUIVO_ESQUEMA = "/schema.sql";

    private final String url;
    private final String usuario;
    private final String senha;

    public ConexaoBanco(String url, String usuario, String senha) {
        this.url = url;
        this.usuario = usuario;
        this.senha = senha;
    }

    /** Abre uma nova conexão. Quem chama deve fechá-la (try-with-resources). */
    public Connection abrirConexao() throws FonteIndisponivelException {
        try {
            return DriverManager.getConnection(url, usuario, senha);
        } catch (SQLException e) {
            throw new FonteIndisponivelException("Não foi possível conectar ao banco de dados (" + url + "): "
                    + e.getMessage(), e);
        }
    }

    /** Cria as tabelas (se ainda não existirem) a partir do schema.sql. */
    public void inicializarEsquema() throws FonteIndisponivelException {
        String script = lerScriptEsquema();
        try (Connection conexao = abrirConexao(); Statement comando = conexao.createStatement()) {
            for (String instrucao : script.split(";")) {
                String sql = removerComentarios(instrucao).trim();
                if (!sql.isEmpty()) {
                    comando.execute(sql); // DDL fixo, lido do arquivo: não há dado do usuário no SQL
                }
            }
        } catch (SQLException e) {
            throw new FonteIndisponivelException("Falha ao criar as tabelas do banco: " + e.getMessage(), e);
        }
    }

    private String lerScriptEsquema() throws FonteIndisponivelException {
        try (InputStream entrada = ConexaoBanco.class.getResourceAsStream(ARQUIVO_ESQUEMA)) {
            if (entrada == null) {
                throw new FonteIndisponivelException("Arquivo " + ARQUIVO_ESQUEMA + " não encontrado no classpath.");
            }
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new FonteIndisponivelException("Não foi possível ler " + ARQUIVO_ESQUEMA + ": " + e.getMessage(), e);
        }
    }

    private static String removerComentarios(String sql) {
        StringBuilder limpo = new StringBuilder();
        for (String linha : sql.split("\\R")) {
            if (!linha.trim().startsWith("--")) {
                limpo.append(linha).append('\n');
            }
        }
        return limpo.toString();
    }

    public String getUrl() {
        return url;
    }
}
