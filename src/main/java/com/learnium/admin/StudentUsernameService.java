package com.learnium.admin;

import com.learnium.common.error.*;
import com.learnium.repository.user.*;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Validates, suggests and reserves centre-scoped student usernames. */
@Service
public class StudentUsernameService {
    private static final Pattern FORMAT = Pattern.compile("[a-z0-9][a-z0-9.]{1,28}[a-z0-9]");
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private final UserAccountRepository users;
    private final CentreRepository centres;

    public StudentUsernameService(UserAccountRepository users, CentreRepository centres) {
        this.users = users;
        this.centres = centres;
    }

    public boolean available(UUID centreId, String value) {
        String normalized = normalize(value);
        return valid(normalized) && !users.existsByCentreIdAndUsername(centreId, normalized);
    }

    @Transactional
    public String choose(UUID centreId, String displayName, String requested) {
        centres.lockById(centreId).orElseThrow(() -> new BusinessException(ErrorCode.SYS_NOT_FOUND));
        if (requested != null && !requested.isBlank()) {
            String value = normalize(requested);
            if (!valid(value) || users.existsByCentreIdAndUsername(centreId, value)) throw invalid();
            return value;
        }
        String base = suggestion(displayName);
        if (!users.existsByCentreIdAndUsername(centreId, base)) return base;
        for (int suffix = 2; ; suffix++) {
            String number = Integer.toString(suffix);
            String value = base.substring(0, Math.min(base.length(), 30 - number.length())) + number;
            if (!users.existsByCentreIdAndUsername(centreId, value)) return value;
        }
    }

    public String requireAvailable(UUID centreId, String requested, UUID currentUserId) {
        centres.lockById(centreId).orElseThrow(() -> new BusinessException(ErrorCode.SYS_NOT_FOUND));
        String value = normalize(requested);
        if (!valid(value)) throw invalid();
        var existing = users.findByCentreIdAndUserId(centreId, currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USR_NOT_FOUND));
        if (!value.equals(existing.getUsername()) && users.existsByCentreIdAndUsername(centreId, value)) throw invalid();
        return value;
    }

    static String suggestion(String displayName) {
        String ascii = MARKS.matcher(Normalizer.normalize(
                displayName == null ? "" : displayName.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)).replaceAll("");
        List<String> words = Arrays.stream(NON_ALNUM.split(ascii)).filter(word -> !word.isBlank()).toList();
        if (words.isEmpty()) return "student";
        String value = words.get(0);
        if (words.size() > 1) value += "." + words.get(words.size() - 1).charAt(0);
        value = value.substring(0, Math.min(30, value.length()));
        return value.length() >= 3 ? value : (value + "student").substring(0, 3);
    }

    static String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    static boolean valid(String value) { return value.length() >= 3 && value.length() <= 30 && FORMAT.matcher(value).matches() && !value.contains(".."); }
    private BusinessException invalid() {
        return new BusinessException(ErrorCode.USR_USERNAME_INVALID, ErrorCode.USR_USERNAME_INVALID.getDefaultMessage(),
                Map.of("username", "Use an available 3–30 character lowercase username with letters, numbers and dots"));
    }
}
