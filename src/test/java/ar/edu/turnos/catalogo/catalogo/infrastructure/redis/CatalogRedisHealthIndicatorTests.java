package ar.edu.turnos.catalogo.catalogo.infrastructure.redis;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogRedisHealthIndicatorTests {
    @Test
    void connectionFailureIsDownAndDoesNotExposeExceptionDetails() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("sensitive-connection-details"));

        var result = new CatalogRedisHealthIndicator(redis).health();

        assertThat(result.getStatus().getCode()).isEqualTo("DOWN");
        assertThat(result.getDetails()).containsOnlyKeys("reason")
                .containsEntry("reason", "REDIS_READ_FAILED");
        assertThat(result.toString()).doesNotContain("sensitive-connection-details");
    }
}
