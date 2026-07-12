package com.dsdb.cactoflix.service;
import com.dsdb.cactoflix.model.Movie;
import com.dsdb.cactoflix.repository.MovieRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@AllArgsConstructor
@Service
public class MovieService {
    private final MovieRepository movieRepository;

    public Movie findById(Long id) {
        return movieRepository.findMovieById(id);
    }

    public List<Movie> getMovies(String name,List<String> genre){
        return movieRepository.findMovieByfilters(genre, name);
    }
}
