package com.learnium.exam;

import com.learnium.repository.exam.ExamInstanceRepository;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class JoinCodes {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();
    private final ExamInstanceRepository instances;
    public JoinCodes(ExamInstanceRepository instances) { this.instances = instances; }
    public String next() {
        String code;
        do {
            StringBuilder text = new StringBuilder(8);
            for (int i = 0; i < 8; i++) text.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            code = text.toString();
        } while (instances.existsByJoinCode(code));
        return code;
    }
}
