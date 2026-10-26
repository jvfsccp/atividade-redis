package br.pucgoias.ads.filmes;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "filme")
public class Filme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String titulo;
    private String diretor;
    private Integer ano;
    private BigDecimal nota;

    protected Filme() { }

    public Long getId() { return id; }
    public String getTitulo() { return titulo; }
    public String getDiretor() { return diretor; }
    public Integer getAno() { return ano; }
    public BigDecimal getNota() { return nota; }

    public void alterarNota(BigDecimal novaNota) {
        this.nota = novaNota;
    }
}
