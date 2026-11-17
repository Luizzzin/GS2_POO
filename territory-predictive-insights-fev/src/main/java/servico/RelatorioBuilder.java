package servico;

import dominio.Alerta;
import dominio.DadosTerritorio;
import dominio.Limiares;
import dominio.PontoMonitoramento;
import dominio.TipoSensor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PADRÃO BUILDER: monta o relatório de "status completo" passo a passo.
 *
 * O relatório tem várias partes opcionais (território, pontos, sensores, limiares,
 * previsões, alertas). Com o Builder, quem monta escolhe as partes em sequência
 * legível ({@code .comTerritorio(...).comAlertas(...)}) em vez de um construtor
 * com muitos parâmetros.
 */
public class RelatorioBuilder {

    private String titulo;
    private final Map<String, List<String>> secoes = new LinkedHashMap<>();

    public static RelatorioBuilder novo() {
        return new RelatorioBuilder();
    }

    public RelatorioBuilder comTitulo(String titulo) {
        this.titulo = titulo;
        return this;
    }

    public RelatorioBuilder comTerritorio(DadosTerritorio dados) {
        return comSecao("Território", List.of(dados.toString()));
    }

    public RelatorioBuilder comPontosMonitorados(List<PontoMonitoramento> pontos) {
        List<String> linhas = new ArrayList<>();
        for (PontoMonitoramento ponto : pontos) {
            ponto.verificarStatus();
            linhas.add(ponto.descrever());
        }
        return comSecao("Pontos vulneráveis monitorados", linhas);
    }

    public RelatorioBuilder comLimiares(Map<TipoSensor, Limiares> limiares) {
        List<String> linhas = new ArrayList<>();
        limiares.forEach((tipo, l) -> linhas.add(String.format("%-16s (%s): %s", tipo.getNomeExibicao(),
                tipo.getUnidade(), l)));
        return comSecao("Limiares em vigor", linhas);
    }

    public RelatorioBuilder comAlertasRecentes(List<Alerta> alertas, int quantidadeMaxima) {
        List<String> linhas = new ArrayList<>();
        alertas.stream().limit(quantidadeMaxima).forEach(a -> linhas.add(a.toString()));
        if (linhas.isEmpty()) {
            linhas.add("Nenhum alerta emitido até agora.");
        } else if (alertas.size() > quantidadeMaxima) {
            linhas.add("(... e mais " + (alertas.size() - quantidadeMaxima) + " alerta(s) no histórico)");
        }
        return comSecao("Alertas mais recentes (" + alertas.size() + " no total)", linhas);
    }

    /** Seção livre, para qualquer outro conteúdo. */
    public RelatorioBuilder comSecao(String nome, List<String> linhas) {
        secoes.put(nome, List.copyOf(linhas));
        return this;
    }

    public Relatorio construir() {
        if (titulo == null || titulo.isBlank()) {
            throw new IllegalStateException("O relatório precisa de um título (use comTitulo).");
        }
        if (secoes.isEmpty()) {
            throw new IllegalStateException("O relatório precisa de pelo menos uma seção.");
        }
        return new Relatorio(titulo, LocalDateTime.now(), Collections.unmodifiableMap(new LinkedHashMap<>(secoes)));
    }
}
