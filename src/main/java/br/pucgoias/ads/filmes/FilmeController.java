package br.pucgoias.ads.filmes;

import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/filmes")
public class FilmeController {

    private static final Logger log = LoggerFactory.getLogger(FilmeController.class);

    private final FilmeService servico;
    private final StringRedisTemplate redis;

    public FilmeController(FilmeService servico, StringRedisTemplate redis) {
        this.servico = servico;
        this.redis = redis;
    }

    @GetMapping("/{id}")
    public FilmeResposta buscar(@PathVariable Long id) {
        long inicio = System.currentTimeMillis();
        contarAcesso(id);
        FilmeResposta resposta = servico.buscar(id);
        log.info("GET /filmes/{} atendida em {} ms", id, System.currentTimeMillis() - inicio);
        return resposta;
    }

    /**
     * Atividade complementar 4. Fica no controlador porque, em um acerto, o Spring devolve
     * a cópia e o método de FilmeService (anotado com @Cacheable) nem chega a ser executado:
     * um contador ali dentro só contaria as faltas.
     */
    private void contarAcesso(Long id) {
        try {
            redis.opsForValue().increment("filme:" + id + ":acessos");
        } catch (RuntimeException e) {
            log.warn("Contador de acessos indisponível para o filme {}", id);
        }
    }

    @PutMapping("/{id}/nota")
    public FilmeResposta alterarNota(@PathVariable Long id, @RequestParam BigDecimal valor) {
        return servico.alterarNota(id, valor);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable Long id) {
        servico.remover(id);
    }
}
