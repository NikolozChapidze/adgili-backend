package li.adgi.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "adgili.jwt")
public class JwtProperties {
    private String secret;
    private int accessTokenTtlMinutes = 60;
    private int refreshTokenTtlDays = 30;
    private String issuer = "adgi.li";
}
