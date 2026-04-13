package com.civicworks.platform;

import com.civicworks.moderation.application.CommentFilterService;
import com.civicworks.moderation.domain.SensitiveWord;
import com.civicworks.moderation.infra.SensitiveWordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentFilterServiceTest {

    @Mock
    private SensitiveWordRepository sensitiveWordRepo;

    private CommentFilterService filterService;

    @BeforeEach
    void setUp() {
        filterService = new CommentFilterService(sensitiveWordRepo);
    }

    private SensitiveWord word(String w) {
        SensitiveWord sw = new SensitiveWord();
        sw.setWord(w);
        sw.setActive(true);
        return sw;
    }

    @Test
    void noHitsWhenNoSensitiveWords() {
        when(sensitiveWordRepo.findByActiveTrue()).thenReturn(List.of());
        assertEquals(0, filterService.countSensitiveWordHits("some text here"));
    }

    @Test
    void countsExactCaseInsensitiveMatches() {
        when(sensitiveWordRepo.findByActiveTrue()).thenReturn(List.of(word("bad"), word("evil")));
        assertEquals(2, filterService.countSensitiveWordHits("This is BAD and Evil"));
    }

    @Test
    void countsMultipleOccurrencesOfSameWord() {
        when(sensitiveWordRepo.findByActiveTrue()).thenReturn(List.of(word("spam")));
        assertEquals(3, filterService.countSensitiveWordHits("spam spam spam"));
    }

    @Test
    void twoOrMoreHitsTriggerHoldForReview() {
        when(sensitiveWordRepo.findByActiveTrue()).thenReturn(List.of(word("bad"), word("evil")));
        int hits = filterService.countSensitiveWordHits("bad evil content");
        assertTrue(hits >= 2, "Should have 2+ hits to trigger hold for review");
    }

    @Test
    void exactMatchOnly_doesNotMatchSubstrings() {
        when(sensitiveWordRepo.findByActiveTrue()).thenReturn(List.of(word("bad")));
        // "badminton" / "embedded" must not count as a hit on "bad".
        assertEquals(0, filterService.countSensitiveWordHits("I love badminton."));
        assertEquals(0, filterService.countSensitiveWordHits("It is embedded."));
    }

    @Test
    void exactMatch_caseInsensitive_matchesAtWordBoundary() {
        when(sensitiveWordRepo.findByActiveTrue()).thenReturn(List.of(word("bad")));
        assertEquals(1, filterService.countSensitiveWordHits("bad"));
        assertEquals(1, filterService.countSensitiveWordHits("That was BAD!"));
        // Punctuation around the word still counts as a boundary.
        assertEquals(1, filterService.countSensitiveWordHits("(bad)"));
    }

    @Test
    void nullOrBlankTextReturnsZero() {
        assertEquals(0, filterService.countSensitiveWordHits(null));
        assertEquals(0, filterService.countSensitiveWordHits("   "));
    }
}
