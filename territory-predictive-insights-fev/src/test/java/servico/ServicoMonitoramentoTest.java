package servico;

import dominio.Alerta;
import dominio.DadosTerritorio;
import dominio.Limiares;
import dominio.Medicao;
import dominio.NivelAlerta;
import dominio.PontoMonitoramento;
import dominio.Sensor;
import dominio.TipoSensor;
import excecao.DadosInvalidosException;
import excecao.FonteIndisponivelException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import persistencia.AlertaDAO;
import persistencia.MedicaoDAO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testa a lógica de leitura, previsão e alerta SEM banco de dados:
 * MedicaoDAO e AlertaDAO são mocks do Mockito.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServicoMonitoramentoTest {

    private static final String CODIGO = "PLV-01";

    @Mock
    private MedicaoDAO medicaoDAO;
    @Mock
    private AlertaDAO alertaDAO;
    @Mock
    private Sensor pluviometro;

    private ServicoMonitoramento servico;

    @BeforeEach
    void configurar() throws Exception {
        ConfiguracaoSistema configuracao = ConfiguracaoSistema.getInstance();
        configuracao.restaurarPadroes(); // limiares de chuva: 10 / 30 / 50 mm/h

        when(pluviometro.getCodigo()).thenReturn(CODIGO);
        when(pluviometro.getTipo()).thenReturn(TipoSensor.PLUVIOMETRO);
        when(pluviometro.getLocalizacao()).thenReturn("Baixada Teste");
        when(pluviometro.verificarFuncionamento()).thenReturn(true);
        // o DAO "grava" devolvendo o próprio objeto
        when(medicaoDAO.salvar(any(Medicao.class))).thenAnswer(inv -> inv.getArgument(0));
        when(alertaDAO.salvar(any(Alerta.class))).thenAnswer(inv -> inv.getArgument(0));

        PontoMonitoramento ponto = new PontoMonitoramento("PT-T", "Ponto Teste", "Baixada Teste", "alagamento");
        ponto.adicionarSensor(pluviometro);
        DadosTerritorio territorio = new DadosTerritorio("Território Teste", "Zona Teste", 100, 10, "fiap2026");
        servico = new ServicoMonitoramento(medicaoDAO, alertaDAO, List.of(ponto), territorio, configuracao,
                new FabricaPrevisao());
    }

    private static List<Medicao> leiturasConstantes(double valor, int quantidade) throws DadosInvalidosException {
        List<Medicao> lista = new ArrayList<>();
        LocalDateTime inicio = LocalDateTime.of(2026, 11, 14, 0, 0);
        for (int i = 0; i < quantidade; i++) {
            lista.add(new Medicao((long) i + 1, CODIGO, TipoSensor.PLUVIOMETRO, valor, inicio.plusMinutes(10L * i)));
        }
        return lista;
    }

    @Test
    @DisplayName("Previsão acima do limite CRÍTICO grava um alerta CRÍTICO explicando o motivo")
    void deveEmitirAlertaCriticoQuandoPrevisaoEstouraLimite() throws Exception {
        // Arrange: 10 leituras de 60 mm/h (limite crítico = 50 mm/h)
        when(medicaoDAO.buscarUltimasPorSensor(eq(CODIGO), anyInt())).thenReturn(leiturasConstantes(60.0, 10));

        // Act
        RodadaPrevisao rodada = servico.rodarPrevisoes();

        // Assert
        ArgumentCaptor<Alerta> captor = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaDAO).salvar(captor.capture()); // exatamente 1 alerta (o mais grave do sensor)
        Alerta alerta = captor.getValue();
        assertEquals(NivelAlerta.CRITICO, alerta.getNivel());
        assertEquals(50.0, alerta.getLimite(), 0.0001);
        assertEquals(60.0, alerta.getValorPrevisto(), 0.0001);
        assertTrue(alerta.getMensagem().contains(CODIGO), alerta.getMensagem());
        assertTrue(alerta.getMensagem().contains("CRÍTICO"), alerta.getMensagem());
        assertEquals(1, rodada.alertas().size());
    }

    @Test
    @DisplayName("Previsão abaixo de todos os limiares não grava alerta")
    void naoDeveEmitirAlertaAbaixoDoLimiar() throws Exception {
        // Arrange: chuva fraca constante de 3 mm/h
        when(medicaoDAO.buscarUltimasPorSensor(eq(CODIGO), anyInt())).thenReturn(leiturasConstantes(3.0, 10));

        // Act
        RodadaPrevisao rodada = servico.rodarPrevisoes();

        // Assert
        verify(alertaDAO, never()).salvar(any());
        assertTrue(rodada.alertas().isEmpty());
        assertTrue(rodada.previsoes().stream().allMatch(p -> p.isConfiavel()));
    }

    @Test
    @DisplayName("Com menos de 5 leituras no banco o sistema se cala, mesmo com chuva extrema")
    void deveFicarEmSilencioComPoucasLeituras() throws Exception {
        // Arrange: só 3 leituras, todas altíssimas
        when(medicaoDAO.buscarUltimasPorSensor(eq(CODIGO), anyInt())).thenReturn(leiturasConstantes(120.0, 3));

        // Act
        RodadaPrevisao rodada = servico.rodarPrevisoes();

        // Assert
        verify(alertaDAO, never()).salvar(any());
        assertTrue(rodada.previsoes().stream().noneMatch(p -> p.isConfiavel()));
    }

    @Test
    @DisplayName("Leitura válida do sensor é gravada no banco")
    void deveGravarMedicaoValida() throws Exception {
        // Arrange
        when(pluviometro.lerValor()).thenReturn(12.0);

        // Act
        ResultadoLeitura resultado = servico.lerESalvar(pluviometro);

        // Assert
        assertTrue(resultado.foiGravada());
        ArgumentCaptor<Medicao> captor = ArgumentCaptor.forClass(Medicao.class);
        verify(medicaoDAO).salvar(captor.capture());
        assertEquals(12.0, captor.getValue().getValor(), 0.0001);
    }

    @Test
    @DisplayName("Leitura de chuva negativa é descartada e NÃO vai para o banco")
    void naoDeveGravarLeituraInvalida() throws Exception {
        // Arrange
        when(pluviometro.lerValor()).thenReturn(-4.0);

        // Act
        ResultadoLeitura resultado = servico.lerESalvar(pluviometro);

        // Assert
        assertEquals(ResultadoLeitura.Situacao.INVALIDA, resultado.situacao());
        assertTrue(resultado.mensagem().contains("chuva negativa"), resultado.mensagem());
        verify(medicaoDAO, never()).salvar(any());
    }

    @Test
    @DisplayName("Sensor sem resposta gera falha explicada e nada é gravado")
    void naoDeveGravarQuandoSensorFalha() throws Exception {
        // Arrange
        when(pluviometro.lerValor()).thenThrow(new FonteIndisponivelException("Sensor PLV-01 não respondeu."));

        // Act
        ResultadoLeitura resultado = servico.lerESalvar(pluviometro);

        // Assert
        assertEquals(ResultadoLeitura.Situacao.FALHA, resultado.situacao());
        assertFalse(resultado.foiGravada());
        assertTrue(resultado.mensagem().contains("não respondeu"));
        verify(medicaoDAO, never()).salvar(any());
    }

    @Test
    @DisplayName("Alterar limiares exige o código de acesso; com o código certo, muda o alerta emitido")
    void deveAlterarLimiaresSomenteComCodigoCorreto() throws Exception {
        // Arrange: 10 leituras de 20 mm/h -> com os limiares padrão é só ATENÇÃO
        when(medicaoDAO.buscarUltimasPorSensor(eq(CODIGO), anyInt())).thenReturn(leiturasConstantes(20.0, 10));

        // Act
        assertThrows(DadosInvalidosException.class,
                () -> servico.alterarLimiares("senha-errada", TipoSensor.PLUVIOMETRO, 5, 10, 15));
        Limiares novos = servico.alterarLimiares("fiap2026", TipoSensor.PLUVIOMETRO, 5, 10, 15);
        RodadaPrevisao rodada = servico.rodarPrevisoes();

        // Assert
        assertEquals(15.0, novos.getCritico(), 0.0001);
        assertEquals(NivelAlerta.CRITICO, rodada.alertas().get(0).getNivel());
    }
}
