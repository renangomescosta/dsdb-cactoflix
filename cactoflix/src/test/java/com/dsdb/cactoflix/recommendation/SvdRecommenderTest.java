package com.dsdb.cactoflix.recommendation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Usa o svd_model.json real (src/main/resources/recommendation), sem mocks:
 * a lógica de álgebra do fold-in só faz sentido validada contra o modelo de verdade.
 * IDs de filme abaixo (2683, 904, 3717, 1721) vêm das primeiras entradas de itemIds do modelo.
 */
class SvdRecommenderTest {

    private static final long ITEM_1 = 2683L;
    private static final long ITEM_2 = 904L;
    private static final long ITEM_3 = 3717L;
    private static final long ITEM_4 = 1721L;
    private static final long UNKNOWN_ITEM = -1L;

    private SvdRecommender recommender;

    @BeforeEach
    void setUp() {
        recommender = new SvdRecommender();
    }

    @Test
    void globalMeanMatchesModelFile() {
        assertThat(recommender.globalMean()).isEqualTo(3.581489863990892);
    }

    @Test
    void fewerThanMinValidRatingsReturnsEmptyList() {
        Map<Long, Integer> rated = new HashMap<>();
        rated.put(ITEM_1, 5);
        rated.put(ITEM_2, 4);
        assertThat(rated).hasSizeLessThan(SvdRecommender.MIN_VALID_RATINGS);

        List<SvdRecommender.ScoredMovie> result = recommender.recommend(rated, 5);

        assertThat(result).isEmpty();
    }

    @Test
    void nullRatingIsIgnoredAndDoesNotCountTowardsThreshold() {
        Map<Long, Integer> rated = new HashMap<>();
        rated.put(ITEM_1, 5);
        rated.put(ITEM_2, 4);
        rated.put(ITEM_3, null); // avaliação sem nota: não computa

        List<SvdRecommender.ScoredMovie> result = recommender.recommend(rated, 5);

        assertThat(result).isEmpty();
    }

    @Test
    void unknownItemIsIgnoredAndDoesNotCountTowardsThreshold() {
        Map<Long, Integer> rated = new HashMap<>();
        rated.put(ITEM_1, 5);
        rated.put(ITEM_2, 4);
        rated.put(UNKNOWN_ITEM, 3); // filme fora do modelo: ignorado

        List<SvdRecommender.ScoredMovie> result = recommender.recommend(rated, 5);

        assertThat(result).isEmpty();
    }

    @Test
    void withEnoughValidRatingsReturnsNonEmptyBoundedResult() {
        Map<Long, Integer> rated = new HashMap<>();
        rated.put(ITEM_1, 5);
        rated.put(ITEM_2, 4);
        rated.put(ITEM_3, 3);
        rated.put(UNKNOWN_ITEM, 5); // deve ser ignorado silenciosamente

        List<SvdRecommender.ScoredMovie> result = recommender.recommend(rated, 5);

        assertThat(result).isNotEmpty();
        assertThat(result.size()).isLessThanOrEqualTo(5);
    }

    @Test
    void recommendationsExcludeAlreadyRatedMovies() {
        Map<Long, Integer> rated = new HashMap<>();
        rated.put(ITEM_1, 5);
        rated.put(ITEM_2, 4);
        rated.put(ITEM_3, 3);
        rated.put(ITEM_4, 5);

        List<SvdRecommender.ScoredMovie> result = recommender.recommend(rated, 20);

        List<Long> recommendedIds = result.stream().map(SvdRecommender.ScoredMovie::movieId).toList();
        assertThat(recommendedIds).doesNotContain(ITEM_1, ITEM_2, ITEM_3, ITEM_4);
    }

    @Test
    void resultIsSortedByScoreDescending() {
        Map<Long, Integer> rated = new HashMap<>();
        rated.put(ITEM_1, 5);
        rated.put(ITEM_2, 1);
        rated.put(ITEM_3, 4);

        List<SvdRecommender.ScoredMovie> result = recommender.recommend(rated, 10);

        for (int i = 1; i < result.size(); i++) {
            assertThat(result.get(i - 1).score()).isGreaterThanOrEqualTo(result.get(i).score());
        }
    }

    @Test
    void kLimitsResultSize() {
        Map<Long, Integer> rated = new HashMap<>();
        rated.put(ITEM_1, 5);
        rated.put(ITEM_2, 4);
        rated.put(ITEM_3, 3);

        List<SvdRecommender.ScoredMovie> result = recommender.recommend(rated, 2);

        assertThat(result).hasSize(2);
    }
}
