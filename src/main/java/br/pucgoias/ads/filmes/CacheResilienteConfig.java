package br.pucgoias.ads.filmes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;

/** Etapa 10: falhas do Redis são registradas; a consulta segue para o PostgreSQL. */
@Configuration
public class CacheResilienteConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheResilienteConfig.class);

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache indisponível na leitura da chave {}", key);
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache,
                                            Object key, Object value) {
                log.warn("Cache indisponível na gravação da chave {}", key);
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache indisponível na remoção da chave {}", key);
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Cache indisponível na limpeza");
            }
        };
    }
}
