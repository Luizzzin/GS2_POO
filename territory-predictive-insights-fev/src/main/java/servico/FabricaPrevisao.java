package servico;

import dominio.EstrategiaPrevisao;
import dominio.PrevisaoComparacao;
import dominio.PrevisaoMediaMovel;
import excecao.DadosInvalidosException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * PADRÃO FACTORY: cria a estratégia de previsão a partir do NOME configurado
 * (ex.: "MEDIA_MOVEL"), sem nenhum {@code if (tipo == ...)} ou {@code instanceof}.
 *
 * Cada nome aponta para o construtor da estratégia num mapa (catálogo). Para criar
 * uma nova estratégia basta registrá-la aqui: o serviço e o menu não mudam.
 */
public class FabricaPrevisao {

    private final Map<String, IntFunction<EstrategiaPrevisao>> catalogo = new LinkedHashMap<>();

    public FabricaPrevisao() {
        registrar("MEDIA_MOVEL", PrevisaoMediaMovel::new);
        registrar("COMPARACAO", PrevisaoComparacao::new);
    }

    /** Registra (ou substitui) uma estratégia no catálogo. */
    public final void registrar(String nome, IntFunction<EstrategiaPrevisao> construtor) {
        catalogo.put(normalizar(nome), construtor);
    }

    /**
     * Cria a estratégia pelo nome.
     *
     * @throws DadosInvalidosException se o nome não existe no catálogo
     */
    public EstrategiaPrevisao criar(String nome, int janela) throws DadosInvalidosException {
        IntFunction<EstrategiaPrevisao> construtor = Optional.ofNullable(nome)
                .map(FabricaPrevisao::normalizar)
                .map(catalogo::get)
                .orElseThrow(() -> new DadosInvalidosException("estratégia de previsão desconhecida: '" + nome
                        + "'. Disponíveis: " + catalogo.keySet()));
        return construtor.apply(janela);
    }

    /** Cria todas as estratégias ativas na configuração do sistema. */
    public List<EstrategiaPrevisao> criarConfiguradas(ConfiguracaoSistema configuracao) throws DadosInvalidosException {
        List<EstrategiaPrevisao> estrategias = new ArrayList<>();
        for (String nome : configuracao.getEstrategiasAtivas()) {
            estrategias.add(criar(nome, configuracao.getJanelaPrevisao()));
        }
        if (estrategias.isEmpty()) {
            throw new DadosInvalidosException("nenhuma estratégia de previsão configurada em previsao.estrategias.");
        }
        return estrategias;
    }

    public Set<String> nomesDisponiveis() {
        return catalogo.keySet();
    }

    private static String normalizar(String nome) {
        return nome.trim().toUpperCase(Locale.ROOT);
    }
}
