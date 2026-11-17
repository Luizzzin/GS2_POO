package servico;

import dominio.Limiares;
import dominio.TipoSensor;
import excecao.DadosInvalidosException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * PADRÃO SINGLETON: a única fonte de configuração do sistema (limiares, janela
 * de previsão, estratégias ativas e dados de conexão).
 *
 * O construtor é privado e a instância é obtida por {@link #getInstance()}. Assim,
 * quando a coordenação altera um limiar pelo menu, o serviço de alertas enxerga a
 * mudança na hora, porque todos consultam o mesmo objeto.
 */
public final class ConfiguracaoSistema {

    private static final String ARQUIVO = "/config.properties";
    private static ConfiguracaoSistema instancia;

    private final Map<TipoSensor, Limiares> limiares = new EnumMap<>(TipoSensor.class);
    private Properties propriedades = new Properties();
    private int janelaPrevisao;
    private List<String> estrategiasAtivas;

    private ConfiguracaoSistema() {
        restaurarPadroes();
    }

    public static synchronized ConfiguracaoSistema getInstance() {
        if (instancia == null) {
            instancia = new ConfiguracaoSistema();
        }
        return instancia;
    }

    /**
     * Recarrega os valores do config.properties (usado na inicialização e nos testes,
     * para que um teste não herde limiares alterados por outro).
     */
    public synchronized void restaurarPadroes() {
        propriedades = carregarArquivo();
        janelaPrevisao = Integer.parseInt(valor("previsao.janela", "5"));
        if (janelaPrevisao < 1) {
            throw new IllegalStateException("previsao.janela precisa ser pelo menos 1 (valor: " + janelaPrevisao + ").");
        }
        estrategiasAtivas = Arrays.stream(valor("previsao.estrategias", "MEDIA_MOVEL,COMPARACAO").split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        limiares.clear();
        for (TipoSensor tipo : TipoSensor.values()) {
            limiares.put(tipo, lerLimiares(tipo));
        }
    }

    public synchronized Limiares getLimiares(TipoSensor tipo) {
        return limiares.get(tipo);
    }

    public synchronized Map<TipoSensor, Limiares> getTodosLimiares() {
        return Collections.unmodifiableMap(new EnumMap<>(limiares));
    }

    /** Troca os limiares de um tipo de sensor (o objeto Limiares já chega validado). */
    public synchronized void definirLimiares(TipoSensor tipo, Limiares novos) {
        limiares.put(tipo, novos);
    }

    public synchronized int getJanelaPrevisao() {
        return janelaPrevisao;
    }

    public synchronized List<String> getEstrategiasAtivas() {
        return estrategiasAtivas;
    }

    /** Valor de uma chave: primeiro procura em -Dchave=valor, depois no config.properties. */
    public synchronized String valor(String chave, String padrao) {
        return System.getProperty(chave, propriedades.getProperty(chave, padrao));
    }

    public int valorInteiro(String chave, int padrao) {
        String texto = valor(chave, String.valueOf(padrao));
        try {
            return Integer.parseInt(texto.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Configuração '" + chave + "' deveria ser um número inteiro: " + texto, e);
        }
    }

    private Limiares lerLimiares(TipoSensor tipo) {
        String chave = "limiar." + tipo.name();
        String texto = valor(chave, null);
        if (texto == null) {
            throw new IllegalStateException("Configuração obrigatória ausente: " + chave);
        }
        String[] partes = texto.split(",");
        try {
            if (partes.length != 3) {
                throw new DadosInvalidosException("esperados 3 valores separados por vírgula.");
            }
            return new Limiares(Double.parseDouble(partes[0].trim()), Double.parseDouble(partes[1].trim()),
                    Double.parseDouble(partes[2].trim()));
        } catch (DadosInvalidosException | NumberFormatException e) {
            throw new IllegalStateException("Configuração inválida em " + chave + "=" + texto + ": " + e.getMessage(), e);
        }
    }

    private static Properties carregarArquivo() {
        Properties props = new Properties();
        try (InputStream entrada = ConfiguracaoSistema.class.getResourceAsStream(ARQUIVO)) {
            if (entrada == null) {
                throw new IllegalStateException("Arquivo " + ARQUIVO + " não encontrado no classpath.");
            }
            props.load(new InputStreamReader(entrada, StandardCharsets.UTF_8));
            return props;
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível ler " + ARQUIVO + ": " + e.getMessage(), e);
        }
    }
}
