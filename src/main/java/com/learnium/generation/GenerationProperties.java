package com.learnium.generation;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Generation settings ({@code learnium.generation.*}). Provider choice and
 * models are configuration, never code: local development runs Ollama,
 * production runs Gemini, and the quality gate measures whichever provider
 * production will use. Job rows freeze the values they were created under.
 */
@ConfigurationProperties(prefix = "learnium.generation")
@Getter
@Setter
public class GenerationProperties {

    /** Active provider: {@code gemini} (default) or {@code ollama}. */
    private String provider = "gemini";

    /** Whether the scheduled worker polls for jobs (tests drive it manually). */
    private boolean workerEnabled = true;

    /** Delay between worker polls, in milliseconds. */
    private long pollDelayMs = 2000;

    /** How long a claim's lease lasts before the job may be reclaimed. */
    private long leaseDurationSeconds = 300;

    /** Base retry backoff in seconds; the delay grows with the attempt number. */
    private long retryBackoffSeconds = 30;

    /** Per-teacher daily job cap (UTC day) — bounds one teacher's spend. */
    private int maxJobsPerTeacherPerDay = 20;

    /** Per-centre daily job cap (UTC day) — bounds a centre's total spend. */
    private int maxJobsPerCentrePerDay = 100;

    private Gemini gemini = new Gemini();
    private Ollama ollama = new Ollama();

    /** The model name the active provider would use, frozen onto new jobs. */
    public String activeModel() {
        return "ollama".equals(provider) ? ollama.getModel() : gemini.getModel();
    }

    @Getter
    @Setter
    public static class Gemini {
        /** API key (env GEMINI_API_KEY); blank until the gate/production setup. */
        private String apiKey = "";

        private String model = "gemini-2.0-flash";

        /** Per-request timeout in seconds. */
        private long timeoutSeconds = 120;
    }

    @Getter
    @Setter
    public static class Ollama {
        private String baseUrl = "http://localhost:11434";

        private String model = "llama3.1";

        /** Per-request timeout in seconds (local models can be slow). */
        private long timeoutSeconds = 120;
    }
}
