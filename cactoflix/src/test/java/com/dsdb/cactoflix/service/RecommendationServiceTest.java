package com.dsdb.cactoflix.service;

import com.dsdb.cactoflix.model.Movie;
import com.dsdb.cactoflix.model.Rating;
import com.dsdb.cactoflix.recommendation.RecommendedMovie;
import com.dsdb.cactoflix.recommendation.SvdRecommender;
import com.dsdb.cactoflix.repository.MovieRepository;
import com.dsdb.cactoflix.repository.RatingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private RatingRepository ratingRepository;
    @Mock
    private MovieRepository movieRepository;
    @Mock
    private SvdRecommender recommender;

    private RecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        recommendationService = new RecommendationService(ratingRepository, movieRepository, recommender);
    }

    @Test
    void recommendForUserJoinsSvdResultsWithMovieNamesPreservingOrder() {
        int userId = 1;
        List<Rating> userRatings = List.of(
                new Rating(userId, 10, 5),
                new Rating(userId, 20, 4),
                new Rating(userId, 30, 3));
        when(ratingRepository.findByUser(userId)).thenReturn(userRatings);

        Map<Long, Integer> expectedRatedMap = Map.of(10L, 5, 20L, 4, 30L, 3);
        when(recommender.recommend(eq(expectedRatedMap), eq(5))).thenReturn(List.of(
                new SvdRecommender.ScoredMovie(40L, 4.5),
                new SvdRecommender.ScoredMovie(50L, 4.0)));

        when(movieRepository.fetchData()).thenReturn(List.of(
                new Movie(40L, "Movie D", List.of("Drama")),
                new Movie(50L, "Movie E", List.of("Comedy"))));

        List<RecommendedMovie> result = recommendationService.recommendForUser(userId, 5);

        assertThat(result).containsExactly(
                new RecommendedMovie(40L, "Movie D", 4.5),
                new RecommendedMovie(50L, "Movie E", 4.0));
        verify(ratingRepository, never()).fetchData();
    }

    @Test
    void recommendFromRatingsDoesNotTouchUserHistory() {
        List<Rating> providedRatings = List.of(
                new Rating(0, 10, 5),
                new Rating(0, 20, 4),
                new Rating(0, 30, 3));

        when(recommender.recommend(any(), eq(3))).thenReturn(List.of(new SvdRecommender.ScoredMovie(40L, 4.5)));
        when(movieRepository.fetchData()).thenReturn(List.of(new Movie(40L, "Movie D", List.of("Drama"))));

        List<RecommendedMovie> result = recommendationService.recommendFromRatings(providedRatings, 3);

        assertThat(result).containsExactly(new RecommendedMovie(40L, "Movie D", 4.5));
        verify(ratingRepository, never()).findByUser(any(Integer.class));
    }

    @Test
    void fallsBackToPopularityWhenSvdReturnsEmpty() {
        int userId = 1;
        // O usuário já avaliou o filme 300: deve ficar de fora do fallback.
        when(ratingRepository.findByUser(userId)).thenReturn(List.of(new Rating(userId, 300, 5)));
        when(recommender.recommend(any(), eq(5))).thenReturn(List.of());

        // Filme 100: notas [5,5,5] -> n=3, soma=15
        // Filme 200: nota [1]     -> n=1, soma=1
        // Filme 300: nota [5]     -> excluído (já avaliado pelo usuário)
        // globalAvg sobre TODAS as notas = (15+1+5)/5 = 4.2
        when(ratingRepository.fetchData()).thenReturn(List.of(
                new Rating(11, 100, 5),
                new Rating(12, 100, 5),
                new Rating(13, 100, 5),
                new Rating(14, 200, 1),
                new Rating(userId, 300, 5)));

        when(movieRepository.fetchData()).thenReturn(List.of(
                new Movie(100L, "Movie A", List.of("Drama")),
                new Movie(200L, "Movie B", List.of("Comedy")),
                new Movie(300L, "Movie C", List.of("Action"))));

        List<RecommendedMovie> result = recommendationService.recommendForUser(userId, 5);

        // score = (soma + DAMPING*globalAvg) / (n + DAMPING), DAMPING = 10
        double expectedScoreA = (15 + 10 * 4.2) / (3 + 10); // 57/13
        double expectedScoreB = (1 + 10 * 4.2) / (1 + 10);  // 43/11

        assertThat(result).hasSize(2);
        assertThat(result.get(0).movieId()).isEqualTo(100L);
        assertThat(result.get(0).name()).isEqualTo("Movie A");
        assertThat(result.get(0).score()).isCloseTo(expectedScoreA, within(1e-9));
        assertThat(result.get(1).movieId()).isEqualTo(200L);
        assertThat(result.get(1).score()).isCloseTo(expectedScoreB, within(1e-9));
        assertThat(result).extracting(RecommendedMovie::movieId).doesNotContain(300L);
    }

    @Test
    void fallbackReturnsEmptyWhenThereAreNoRatingsAtAll() {
        int userId = 1;
        when(ratingRepository.findByUser(userId)).thenReturn(List.of());
        when(recommender.recommend(any(), eq(5))).thenReturn(List.of());
        when(ratingRepository.fetchData()).thenReturn(List.of());

        List<RecommendedMovie> result = recommendationService.recommendForUser(userId, 5);

        assertThat(result).isEmpty();
    }
}
