package com.learnium.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Object-storage configuration ({@code learnium.storage.*}). {@code type}
 * selects the implementation: {@code local} (default, development) or
 * {@code s3} (any S3-compatible endpoint — Cloudflare R2 in production).
 * Credentials are bound from the environment; nothing secret has a default.
 */
@ConfigurationProperties(prefix = "learnium.storage")
@Getter
@Setter
public class StorageProperties {

    private String type = "local";

    /** Root directory of the local-disk implementation. */
    private String localDir = "./data/files";

    private S3 s3 = new S3();

    @Getter
    @Setter
    public static class S3 {
        private String endpoint = "";
        private String bucket = "";
        private String region = "us-east-1";
        private String accessKey = "";
        private String secretKey = "";
    }
}
