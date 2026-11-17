package persistencia;

import dominio.Alerta;
import dominio.Medicao;
import dominio.NivelAlerta;
import dominio.TipoSensor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Teste de integração dos DAOs com um H2 EM MEMÓRIA (banco novo a cada teste),
 * para garantir que o SQL e o mapeamento funcionam de verdade.
 */
class MedicaoDAOIntegracaoTest {

    private MedicaoDAO medicaoDAO;
    private AlertaDAO alertaDAO;

    @BeforeEach
    void criarBancoVazio() throws Exception {
        String url = "jdbc:h2:mem:teste_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        ConexaoBanco conexao = new ConexaoBanco(url, "sa", "");
        conexao.inicializarEsquema();
        medicaoDAO = new MedicaoDAO(conexao);
        alertaDAO = new AlertaDAO(conexao);
    }

    @Test
    @DisplayName("Grava medições e devolve as últimas N em ordem cronológica")
    void deveGravarEBuscarUltimasEmOrdemCronologica() throws Exception {
        // Arrange
        LocalDateTime inicio = LocalDateTime.of(2026, 11, 14, 8, 0);
        for (int i = 1; i <= 7; i++) {
            medicaoDAO.salvar(new Medicao(null, "PLV-01", TipoSensor.PLUVIOMETRO, i, inicio.plusMinutes(i)));
        }
        medicaoDAO.salvar(new Medicao(null, "NIV-01", TipoSensor.NIVEL_CORREGO, 2.0, inicio));

        // Act
        List<Medicao> ultimas = medicaoDAO.buscarUltimasPorSensor("PLV-01", 3);
        List<Medicao> todas = medicaoDAO.buscarHistoricoPorSensor("PLV-01");

        // Assert
        assertEquals(List.of(5.0, 6.0, 7.0), ultimas.stream().map(Medicao::getValor).toList());
        assertEquals(7, todas.size());
        assertEquals(7, medicaoDAO.contarPorSensor("PLV-01"));
        assertNotNull(todas.get(0).getId());
    }

    @Test
    @DisplayName("Grava e lista alertas com todos os campos")
    void deveGravarEListarAlertas() throws Exception {
        // Arrange
        Alerta alerta = new Alerta(null, "PLV-01", TipoSensor.PLUVIOMETRO, NivelAlerta.ALERTA, 35.0, 30.0,
                "Média móvel", "Chuva prevista de 35 mm/h atingiu o limite de ALERTA.", LocalDateTime.now());

        // Act
        Alerta gravado = alertaDAO.salvar(alerta);
        List<Alerta> alertas = alertaDAO.listarTodos();

        // Assert
        assertNotNull(gravado.getId());
        assertEquals(1, alertas.size());
        assertEquals(NivelAlerta.ALERTA, alertas.get(0).getNivel());
        assertEquals(30.0, alertas.get(0).getLimite(), 0.0001);
    }
}
