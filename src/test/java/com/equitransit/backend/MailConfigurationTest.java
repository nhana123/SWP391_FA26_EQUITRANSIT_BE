package com.equitransit.backend;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.assertThat;

/** Resolve committed YAML without starting a datasource or contacting SMTP. */
public class MailConfigurationTest {
    /** @throws IOException if mail config cannot load */
    @Test
    public void baseAndLocalDefaultToOfflineDeliveryWithoutCredentials()
            throws IOException {
        for (String file : new String[]{"application.yml",
                "application-local.yml"}) {
            var env = environment(file);
            assertThat(env.getProperty("app.mail.mode")).isEqualTo("file");
            assertThat(env.getProperty("app.mail.from"))
                    .isEqualTo("no-reply@equitransit.local");
            assertThat(env.getProperty("spring.mail.username")).isEmpty();
            assertThat(env.getProperty("spring.mail.password")).isEmpty();
            assertThat(env.getProperty("spring.mail.host"))
                    .isEqualTo("smtp.gmail.com");
        }
        assertThat(environment("application.yml")
                .getProperty("app.mail.outbox")).isEqualTo(".local/outbox");
    }

    /** @throws IOException if explicit SMTP settings cannot resolve */
    @Test
    public void smtpIsExplicitAndUsesEnvironmentOverrides() throws IOException {
        var env = environment("application-local.yml")
                .withProperty("EQ_MAIL_MODE", "smtp")
                .withProperty("EQ_MAIL_HOST", "smtp.example.invalid")
                .withProperty("EQ_MAIL_USERNAME", "sender@example.invalid")
                .withProperty("EQ_MAIL_PASSWORD", "test-only-placeholder")
                .withProperty("EQ_MAIL_FROM", "sender@example.invalid");
        assertThat(env.getProperty("app.mail.mode")).isEqualTo("smtp");
        assertThat(env.getProperty("spring.mail.host"))
                .isEqualTo("smtp.example.invalid");
        assertThat(env.getProperty("spring.mail.username"))
                .isEqualTo("sender@example.invalid");
        assertThat(env.getProperty("spring.mail.password"))
                .isEqualTo("test-only-placeholder");
    }

    private MockEnvironment environment(String file) throws IOException {
        var env = new MockEnvironment();
        for (var source : new YamlPropertySourceLoader().load(file,
                new ClassPathResource(file))) {
            env.getPropertySources().addLast(source);
        }
        return env;
    }
}
