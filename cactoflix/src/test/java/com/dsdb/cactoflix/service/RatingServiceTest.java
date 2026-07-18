package com.dsdb.cactoflix.service;

import com.dsdb.cactoflix.model.Rating;
import com.dsdb.cactoflix.repository.RatingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatingServiceTest {

    @Mock
    private RatingRepository ratingRepository;

    private RatingService ratingService;

    @BeforeEach
    void setUp() {
        ratingService = new RatingService(ratingRepository);
    }

    @Test
    void publishRatingUpdatesExistingRatingInsteadOfSaving() {
        Rating rating = new Rating(1, 2, 5);
        when(ratingRepository.findByUserAndMovie(1, 2)).thenReturn(Optional.of(rating));

        ratingService.publishRating(rating);

        verify(ratingRepository).update(rating);
        verify(ratingRepository, never()).save(rating);
    }

    @Test
    void publishRatingSavesNewRatingWhenNoneExists() {
        Rating rating = new Rating(1, 2, 5);
        when(ratingRepository.findByUserAndMovie(1, 2)).thenReturn(Optional.empty());

        ratingService.publishRating(rating);

        verify(ratingRepository).save(rating);
        verify(ratingRepository, never()).update(rating);
    }

    @Test
    void getRatingsDelegatesToRepository() {
        List<Rating> expected = List.of(new Rating(1, 2, 5));
        when(ratingRepository.fetchData()).thenReturn(expected);

        assertThat(ratingService.getRatings()).isSameAs(expected);
    }
}
