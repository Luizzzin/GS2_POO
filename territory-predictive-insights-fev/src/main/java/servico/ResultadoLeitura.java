package servico;

import dominio.Medicao;
import dominio.Sensor;

/**
 * Resultado de uma tentativa de leitura de sensor: gravada no banco, descartada
 * por ser inválida, ou não realizada por falha da fonte.
 */
public record ResultadoLeitura(String sensorCodigo, Situacao situacao, Medicao medicao, String mensagem) {

    public enum Situacao { GRAVADA, INVALIDA, FALHA }

    static ResultadoLeitura gravada(Medicao medicao) {
        return new ResultadoLeitura(medicao.getSensorCodigo(), Situacao.GRAVADA, medicao,
                medicao.getTipo().formatar(medicao.getValor()) + " gravado no histórico.");
    }

    static ResultadoLeitura invalida(Sensor sensor, String motivo) {
        return new ResultadoLeitura(sensor.getCodigo(), Situacao.INVALIDA, null,
                "Leitura descartada (DadosInvalidosException): " + motivo);
    }

    static ResultadoLeitura falha(Sensor sensor, String motivo) {
        return new ResultadoLeitura(sensor.getCodigo(), Situacao.FALHA, null,
                "Sem leitura (FonteIndisponivelException): " + motivo);
    }

    public boolean foiGravada() {
        return situacao == Situacao.GRAVADA;
    }

    @Override
    public String toString() {
        return String.format("[%-8s] %-7s %s", situacao, sensorCodigo, mensagem);
    }
}
