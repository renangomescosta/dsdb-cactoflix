package com.dsdb.cactoflix.repository;

import com.dsdb.cactoflix.model.Rating;
import org.springframework.stereotype.Repository;

import javax.swing.text.html.Option;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Repository no Spring Boot => Entrega o mesmo objeto pra todos => Singleton
@Repository
public class RatingRepository {
    //TODO: Aqui entra o banco de dados
    static final String DATABASE_URL = "preencher";

    private final List<Rating> mockedData = new ArrayList<>(List.of(
            new Rating(1, 1, 5),
            new Rating(1, 2, 4),
            new Rating(2, 1, 3),
            new Rating(2, 3, 5),
            new Rating(3, 2, null)
    ));

    public List<Rating> fetchData() {
        //TODO: Aqui entra o banco de dados
        return mockedData;
    }
    public void save(Rating rating){
        //TODO: Aqui entra o banco de dados
        return;
    }
    public void update(Rating rating) {
        //TODO: Aqui entra o banco de dados
        return;
    }
    public Optional<Rating> findByUserAndMovie(int userId, int movieId){
        List<Rating> data = fetchData();
        return data.stream()
                .filter(r -> r.getMovieId() == movieId && r.getUserId() == userId)
                .findFirst();
    }



}