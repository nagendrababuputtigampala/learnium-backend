package com.learnium.admin;

import com.learnium.repository.user.CentreRepository;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Issues the short, immutable code embedded in every student login identifier. */
@Service
public class CentreCodeService {
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private final CentreRepository centres;

    public CentreCodeService(CentreRepository centres) { this.centres = centres; }

    public String issue(String name) {
        String base = base(name);
        if (!centres.existsByCode(base)) return base;
        for (int suffix = 2; ; suffix++) {
            String number = Integer.toString(suffix);
            String code = base.substring(0, Math.min(base.length(), 16 - number.length())) + number;
            if (!centres.existsByCode(code)) return code;
        }
    }

    static String base(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        String ascii = MARKS.matcher(Normalizer.normalize(lower, Normalizer.Form.NFD)).replaceAll("");
        List<String> words = Arrays.stream(NON_ALNUM.split(ascii)).filter(word -> !word.isBlank()).toList();
        String value;
        if (words.size() > 1) {
            StringBuilder initials = new StringBuilder();
            words.forEach(word -> initials.append(word.charAt(0)));
            value = initials.toString();
        } else value = words.isEmpty() ? "centre" : words.get(0);
        return value.substring(0, Math.min(16, value.length()));
    }
}
