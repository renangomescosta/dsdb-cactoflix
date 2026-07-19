package com.dsdb.cactoflix.controller;


import com.dsdb.cactoflix.model.Movie;
import com.dsdb.cactoflix.service.MovieService;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("/movies")
@Profile("app")
public class MovieController {
    private final MovieService movieService;

    @GetMapping
    public List<Movie> getMovies(
         @RequestParam(required = false) String name,
         @RequestParam(required = false) List<String> genre) {
        return movieService.getMovies(name,genre);
    }

    @GetMapping("/{id}")
    public Movie getMovieById(@PathVariable Long id){
        return movieService.findById(id);
    }
}
