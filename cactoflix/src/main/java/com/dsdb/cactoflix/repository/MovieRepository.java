package com.dsdb.cactoflix.repository;

import com.dsdb.cactoflix.model.Movie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

// import java.util.ArrayList;
import java.util.List;


// Repository no String Boot => Entrega o mesmo objeto pra todos => Singleton
@Repository
public class MovieRepository {
    private static final String COLLECTION_NAME = "movies";
    
    private final MongoTemplate mongoTemplate;

    @Autowired
    public MovieRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<Movie> fetchData() {
        return mongoTemplate.findAll(Movie.class, COLLECTION_NAME);
    }

    public List<Movie> findMovieByfilters(List<String> genre, String name) {

        Query query = new Query(Criteria.where("genre").in(genre)
                                .and("name").regex(name));
        return mongoTemplate.find(query, Movie.class);
    }

    public Movie findMovieById(Long id) {
        return mongoTemplate.findById(id, Movie.class);
    }

}
