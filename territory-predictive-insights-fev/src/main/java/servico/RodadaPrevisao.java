package servico;

import dominio.Alerta;
import dominio.ResultadoPrevisao;

import java.util.List;

/** O que uma rodada de previsões produziu: todas as previsões e os alertas emitidos. */
public record RodadaPrevisao(List<ResultadoPrevisao> previsoes, List<Alerta> alertas) {
}
