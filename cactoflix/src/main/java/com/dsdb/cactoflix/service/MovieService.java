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


    public List<Movie> getMovies(Long id,String name,List<String> genre){
        // TODO: Trocar querys para o banco de dados => (-) Latência
        List<Movie> movies = movieRepository.fetchData();
        if (id!=null){
            movies = movies.stream()
                    .filter(movie -> movie.getId().equals(id))
                    .toList();
        }
        if (name!=null){
            movies = movies.stream()
                    .filter(movie -> movie.getName().contains(name))
                    .toList();
        }
        if (genre!=null){
            movies = movies.stream()
                    .filter(movie -> movie.getGenres().containsAll(genre))
                    .toList();
        }
        return movies;
    }
}
