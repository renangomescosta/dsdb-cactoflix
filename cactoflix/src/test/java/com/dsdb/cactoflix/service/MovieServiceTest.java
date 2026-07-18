package com.dsdb.cactoflix.service;

import com.dsdb.cactoflix.model.Movie;
import com.dsdb.cactoflix.repository.MovieRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovieServiceTest {

    @Mock
    private MovieRepository movieRepository;

    private MovieService movieService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        movieService = new MovieService(movieRepository);
    }

    @Test
    void getMoviesDelegatesToRepositoryWithSameFilters() {
        List<Movie> expected = List.of(new Movie(1L, "Toy Story (1995)", List.of("Animation")));
        when(movieRepository.findMovieFilters("toy", List.of("Animation"))).thenReturn(expected);

        List<Movie> result = movieService.getMovies("toy", List.of("Animation"));

        assertThat(result).isSameAs(expected);
        verify(movieRepository).findMovieFilters("toy", List.of("Animation"));
    }

    @Test
    void findByIdDelegatesToRepository() {
        Movie movie = new Movie(1L, "Toy Story (1995)", List.of("Animation"));
        when(movieRepository.findMovieById(1L)).thenReturn(movie);

        Movie result = movieService.findById(1L);

        assertThat(result).isSameAs(movie);
    }
}
