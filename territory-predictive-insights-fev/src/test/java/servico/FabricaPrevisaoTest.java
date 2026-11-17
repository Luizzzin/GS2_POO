package servico;

import dominio.EstrategiaPrevisao;
import dominio.PrevisaoComparacao;
import dominio.PrevisaoMediaMovel;
import excecao.DadosInvalidosException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FabricaPrevisaoTest {

    @Test
    @DisplayName("Factory cria a estratégia certa a partir do nome (sem diferenciar maiúsculas)")
    void deveCriarEstrategiaPeloNome() throws DadosInvalidosException {
        // Arrange
        FabricaPrevisao fabrica = new FabricaPrevisao();

        // Act
        EstrategiaPrevisao media = fabrica.criar("media_movel", 4);
        EstrategiaPrevisao comparacao = fabrica.criar("COMPARACAO", 4);

        // Assert
        assertInstanceOf(PrevisaoMediaMovel.class, media);
        assertInstanceOf(PrevisaoComparacao.class, comparacao);
        assertEquals(4, media.getJanela());
    }

    @Test
    @DisplayName("Nome de estratégia desconhecido lança DadosInvalidosException")
    void deveRejeitarEstrategiaDesconhecida() {
        // Arrange
        FabricaPrevisao fabrica = new FabricaPrevisao();

        // Act / Assert
        assertThrows(DadosInvalidosException.class, () -> fabrica.criar("REDE_NEURAL", 5));
    }

    @Test
    @DisplayName("Cria as estratégias configuradas no Singleton, que tem uma única instância")
    void deveCriarEstrategiasDaConfiguracao() throws DadosInvalidosException {
        // Arrange
        ConfiguracaoSistema configuracao = ConfiguracaoSistema.getInstance();
        configuracao.restaurarPadroes();

        // Act
        List<EstrategiaPrevisao> estrategias = new FabricaPrevisao().criarConfiguradas(configuracao);

        // Assert
        assertSame(configuracao, ConfiguracaoSistema.getInstance());
        assertEquals(2, estrategias.size());
        assertEquals(5, estrategias.get(0).getJanela());
    }
}
