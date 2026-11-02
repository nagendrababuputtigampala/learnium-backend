package com.learnium.generation;

/**
 * Version pins for the generation contract. Both are frozen onto every job
 * (and copied onto every generated question) so a draft always records the
 * exact prompt wording and payload schema it was produced under; changing
 * either means bumping the constant and shipping a new version, never
 * silently reinterpreting old drafts.
 */
public final class GenerationContract {

    public static final String PROMPT_VERSION = "gen-prompt-v1";
    public static final String PAYLOAD_SCHEMA_VERSION = "gen-payload-v1";

    private GenerationContract() {}
}
