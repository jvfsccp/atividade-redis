package br.pucgoias.ads.filmes;

import java.time.Duration;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManagerBuilderCustomizer customizadorDeCache() {
        var json = new Jackson2JsonRedisSerializer<>(FilmeResposta.class);

        RedisCacheConfiguration configuracao = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))       // prazo de validade de cada entrada
                .disableCachingNullValues()            // valores nulos não são armazenados
                .serializeValuesWith(SerializationPair.fromSerializer(json));

        return builder -> builder.withCacheConfiguration("filmes", configuracao);
    }
}
