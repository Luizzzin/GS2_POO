package servico;

import dominio.Medicao;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * BÔNUS: exporta o histórico consultado para CSV (separador ";" e vírgula decimal,
 * formato que o Excel em português abre direto).
 */
public class ExportadorCsv {

    /** Marca de ordem de bytes: faz o Excel reconhecer o arquivo como UTF-8 (acentos corretos). */
    private static final String BOM_UTF8 = "﻿";

    public Path exportar(List<Medicao> medicoes, Path destino) throws IOException {
        Path pasta = destino.toAbsolutePath().getParent();
        if (pasta != null) {
            Files.createDirectories(pasta);
        }
        try (BufferedWriter escritor = Files.newBufferedWriter(destino, StandardCharsets.UTF_8)) {
            escritor.write(BOM_UTF8 + "id;sensor;tipo;valor;unidade;data_hora");
            escritor.newLine();
            for (Medicao m : medicoes) {
                escritor.write(String.join(";", String.valueOf(m.getId()), m.getSensorCodigo(), m.getTipo().name(),
                        String.format("%.2f", m.getValor()).replace('.', ','), m.getTipo().getUnidade(),
                        m.getDataHoraFormatada()));
                escritor.newLine();
            }
        }
        return destino.toAbsolutePath();
    }
}
