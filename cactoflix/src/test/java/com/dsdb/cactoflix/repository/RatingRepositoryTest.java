package com.dsdb.cactoflix.repository;

import com.dsdb.cactoflix.model.Rating;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatingRepositoryTest {

    private static final String COLLECTION_NAME = "ratings";

    @Mock
    private MongoTemplate mongoTemplate;

    private RatingRepository ratingRepository;

    @BeforeEach
    void setUp() {
        ratingRepository = new RatingRepository(mongoTemplate);
    }

    @Test
    void fetchDataUsesRatingsCollection() {
        List<Rating> expected = List.of(new Rating(1, 2, 5));
        when(mongoTemplate.findAll(Rating.class, COLLECTION_NAME)).thenReturn(expected);

        assertThat(ratingRepository.fetchData()).isSameAs(expected);
    }

    @Test
    void saveInsertsIntoRatingsCollection() {
        Rating rating = new Rating(1, 2, 5);

        ratingRepository.save(rating);

        verify(mongoTemplate).insert(rating, COLLECTION_NAME);
    }

    @Test
    void updateSavesIntoRatingsCollection() {
        Rating rating = new Rating(1, 2, 4);

        ratingRepository.update(rating);

        verify(mongoTemplate).save(rating, COLLECTION_NAME);
    }

    @Test
    void findByUserBuildsUserIdCriteriaAgainstRatingsCollection() {
        when(mongoTemplate.find(any(Query.class), eq(Rating.class), eq(COLLECTION_NAME))).thenReturn(List.of());

        ratingRepository.findByUser(42);

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(captor.capture(), eq(Rating.class), eq(COLLECTION_NAME));
        Document queryObject = captor.getValue().getQueryObject();
        assertThat(queryObject.get("userId")).isEqualTo(42);
    }

    @Test
    void findByUserAndMovieReturnsPresentOptionalWhenFound() {
        Rating rating = new Rating(1, 2, 5);
        when(mongoTemplate.findOne(any(Query.class), eq(Rating.class), eq(COLLECTION_NAME))).thenReturn(rating);

        Optional<Rating> result = ratingRepository.findByUserAndMovie(1, 2);

        assertThat(result).contains(rating);
    }

    @Test
    void findByUserAndMovieReturnsEmptyOptionalWhenNotFound() {
        when(mongoTemplate.findOne(any(Query.class), eq(Rating.class), eq(COLLECTION_NAME))).thenReturn(null);

        Optional<Rating> result = ratingRepository.findByUserAndMovie(1, 2);

        assertThat(result).isEmpty();
    }

    @Test
    void findByUserAndMovieBuildsCompositeCriteria() {
        when(mongoTemplate.findOne(any(Query.class), eq(Rating.class), eq(COLLECTION_NAME))).thenReturn(null);

        ratingRepository.findByUserAndMovie(1, 2);

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findOne(captor.capture(), eq(Rating.class), eq(COLLECTION_NAME));
        Document queryObject = captor.getValue().getQueryObject();
        assertThat(queryObject.get("userId")).isEqualTo(1);
        assertThat(queryObject.get("movieId")).isEqualTo(2);
        verify(mongoTemplate, never()).find(any(Query.class), eq(Rating.class), eq(COLLECTION_NAME));
    }
}
