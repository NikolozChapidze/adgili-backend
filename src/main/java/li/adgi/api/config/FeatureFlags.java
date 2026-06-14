package li.adgi.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "adgili.features")
public class FeatureFlags {
    private boolean emailStub = true;
    private boolean smsStub = true;
    private boolean identityAutoApprove = true;
    private boolean paymentsStub = true;
}
