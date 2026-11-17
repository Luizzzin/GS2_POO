package servico;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Relatório pronto (imutável), montado pelo {@link RelatorioBuilder}.
 */
public final class Relatorio {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final String titulo;
    private final LocalDateTime geradoEm;
    private final Map<String, List<String>> secoes;

    Relatorio(String titulo, LocalDateTime geradoEm, Map<String, List<String>> secoes) {
        this.titulo = titulo;
        this.geradoEm = geradoEm;
        this.secoes = secoes;
    }

    public String getTitulo() {
        return titulo;
    }

    public LocalDateTime getGeradoEm() {
        return geradoEm;
    }

    public Map<String, List<String>> getSecoes() {
        return secoes;
    }

    /** Texto formatado para o console. */
    public String formatar() {
        String linha = "=".repeat(78);
        StringBuilder texto = new StringBuilder();
        texto.append(linha).append('\n')
                .append(' ').append(titulo).append('\n')
                .append(" Gerado em ").append(geradoEm.format(FORMATO)).append('\n')
                .append(linha).append('\n');
        secoes.forEach((nome, linhas) -> {
            texto.append('\n').append("-- ").append(nome).append(' ')
                    .append("-".repeat(Math.max(3, 74 - nome.length()))).append('\n');
            linhas.forEach(l -> texto.append("  ").append(l).append('\n'));
        });
        return texto.append(linha).toString();
    }

    @Override
    public String toString() {
        return formatar();
    }
}
