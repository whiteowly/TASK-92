package com.civicworks.moderation.application;

import com.civicworks.moderation.domain.SensitiveWord;
import com.civicworks.moderation.infra.SensitiveWordRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class CommentFilterService {

    private final SensitiveWordRepository sensitiveWordRepository;

    public CommentFilterService(SensitiveWordRepository sensitiveWordRepository) {
        this.sensitiveWordRepository = sensitiveWordRepository;
    }

    public int countSensitiveWordHits(String text) {
        if (text == null || text.isBlank()) return 0;

        List<SensitiveWord> words = sensitiveWordRepository.findByActiveTrue();
        int hits = 0;

        for (SensitiveWord sw : words) {
            if (sw.getWord() == null || sw.getWord().isBlank()) continue;
            // Exact, case-insensitive token match using Unicode word boundaries.
            // "bad" must match "bad"/"BAD" but NOT "badminton" or "embedded".
            String pattern = "(?iu)(?<!\\p{L}|\\p{N})"
                    + Pattern.quote(sw.getWord().trim())
                    + "(?!\\p{L}|\\p{N})";
            java.util.regex.Matcher matcher = Pattern.compile(pattern).matcher(text);
            while (matcher.find()) {
                hits++;
            }
        }
        return hits;
    }
}
