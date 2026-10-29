package com.learnium.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.learnium.config.MailConfig;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MailTemplateRendererTest {

    private final MailTemplateRenderer renderer =
            new MailTemplateRenderer(new MailConfig().mailTemplateEngine());

    @Test
    void rendersInviteTemplateWithModelValues() {
        String html =
                renderer.render(
                        "invite",
                        Map.of(
                                "displayName", "Tess Teacher",
                                "centreName", "Bright Minds",
                                "link", "http://localhost:5173/set-password?token=abc&purpose=invite",
                                "expiresHours", "48"));

        assertThat(html)
                .contains("Tess Teacher")
                .contains("Bright Minds")
                .contains("set-password?token=abc&amp;purpose=invite")
                .contains("48");
    }

    @Test
    void rendersResetTemplateWithModelValues() {
        String html =
                renderer.render(
                        "reset",
                        Map.of(
                                "displayName", "Tess Teacher",
                                "centreName", "Bright Minds",
                                "link", "http://localhost:5173/set-password?token=xyz&purpose=reset",
                                "expiresHours", "1"));

        assertThat(html).contains("reset the password").contains("Bright Minds");
    }

    @Test
    void plainTextFallbackStripsTagsAndNormalisesEntities() {
        String text = renderer.toPlainText("<p>Hello &amp; welcome</p><p>Line<br/>break &lt;here&gt;</p>");

        assertThat(text)
                .contains("Hello & welcome")
                .contains("break <here>")
                .doesNotContain("<p>")
                .doesNotContain("&amp;");
    }
}
