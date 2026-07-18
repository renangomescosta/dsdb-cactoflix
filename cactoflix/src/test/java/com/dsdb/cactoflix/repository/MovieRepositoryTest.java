package com.dsdb.cactoflix.repository;

import com.dsdb.cactoflix.model.Movie;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovieRepositoryTest {

    private static final String COLLECTION_NAME = "movies";

    @Mock
    private MongoTemplate mongoTemplate;

    private MovieRepository movieRepository;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        movieRepository = new MovieRepository(mongoTemplate);
    }

    @Test
    void fetchDataUsesMoviesCollection() {
        List<Movie> expected = List.of(new Movie(1L, "Toy Story (1995)", List.of("Animation")));
        when(mongoTemplate.findAll(Movie.class, COLLECTION_NAME)).thenReturn(expected);

        List<Movie> result = movieRepository.fetchData();

        assertThat(result).isSameAs(expected);
    }

    @Test
    void findMovieByIdUsesMoviesCollection() {
        Movie movie = new Movie(1L, "Toy Story (1995)", List.of("Animation"));
        when(mongoTemplate.findById(1L, Movie.class, COLLECTION_NAME)).thenReturn(movie);

        Movie result = movieRepository.findMovieById(1L);

        assertThat(result).isSameAs(movie);
        verify(mongoTemplate).findById(1L, Movie.class, COLLECTION_NAME);
    }

    @Test
    void findMovieFiltersWithNoFiltersQueriesMoviesCollectionWithEmptyCriteria() {
        when(mongoTemplate.find(any(Query.class), eq(Movie.class), eq(COLLECTION_NAME))).thenReturn(List.of());

        movieRepository.findMovieFilters(null, null);

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(captor.capture(), eq(Movie.class), eq(COLLECTION_NAME));
        Document queryObject = captor.getValue().getQueryObject();
        assertThat(queryObject).isEmpty();
    }

    @Test
    void findMovieFiltersWithBlankNameAndEmptyGenreListAddsNoCriteria() {
        when(mongoTemplate.find(any(Query.class), eq(Movie.class), eq(COLLECTION_NAME))).thenReturn(List.of());

        movieRepository.findMovieFilters("   ", List.of());

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(captor.capture(), eq(Movie.class), eq(COLLECTION_NAME));
        assertThat(captor.getValue().getQueryObject()).isEmpty();
    }

    @Test
    void findMovieFiltersByNameBuildsCaseInsensitiveRegexCriteria() {
        when(mongoTemplate.find(any(Query.class), eq(Movie.class), eq(COLLECTION_NAME))).thenReturn(List.of());

        movieRepository.findMovieFilters("toy", null);

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(captor.capture(), eq(Movie.class), eq(COLLECTION_NAME));
        Document queryObject = captor.getValue().getQueryObject();
        assertThat(queryObject).containsKey("name");
        Pattern namePattern = (Pattern) queryObject.get("name");
        assertThat(namePattern.pattern()).isEqualTo("toy");
        assertThat(namePattern.flags() & Pattern.CASE_INSENSITIVE).isNotZero();
    }

    @Test
    void findMovieFiltersByGenreUsesGenresFieldToMatchStoredDocuments() {
        when(mongoTemplate.find(any(Query.class), eq(Movie.class), eq(COLLECTION_NAME))).thenReturn(List.of());

        movieRepository.findMovieFilters(null, List.of("Comedy", "Drama"));

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(captor.capture(), eq(Movie.class), eq(COLLECTION_NAME));
        Document queryObject = captor.getValue().getQueryObject();

        // Regression guard: the Movie document/model field is "genres" (plural), not "genre".
        assertThat(queryObject).containsKey("genres");
        assertThat(queryObject).doesNotContainKey("genre");
        Document genresCriteria = (Document) queryObject.get("genres");
        assertThat((List<String>) genresCriteria.get("$in")).containsExactly("Comedy", "Drama");
    }
}
