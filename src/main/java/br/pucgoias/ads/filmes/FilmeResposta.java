package br.pucgoias.ads.filmes;

import java.math.BigDecimal;

/** Dados devolvidos pela API e, a partir da Etapa 6, armazenados no Redis (JSON). */
public record FilmeResposta(Long id, String titulo, String diretor,
                            Integer ano, BigDecimal nota) {

    public static FilmeResposta de(Filme filme) {
        return new FilmeResposta(filme.getId(), filme.getTitulo(), filme.getDiretor(),
                filme.getAno(), filme.getNota());
    }
}
