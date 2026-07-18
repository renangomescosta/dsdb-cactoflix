package com.dsdb.cactoflix.repository;

import com.dsdb.cactoflix.model.Movie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

// import java.util.ArrayList;
import java.util.List;


// Repository no String Boot => Entrega o mesmo objeto pra todos => Singleton
@Repository
@Profile("app")
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

    public List<Movie> findMovieFilters(String name ,List<String> genre) {

        Query query = new Query();

        if (name != null && !name.isBlank()){
            query.addCriteria(Criteria.where("name").regex(name,"i"));
        }
        if (genre != null && !genre.isEmpty()){
            query.addCriteria(Criteria.where("genres").in(genre));
        }
        return mongoTemplate.find(query, Movie.class, COLLECTION_NAME);
    }

    public Movie findMovieById(Long id) {
        return mongoTemplate.findById(id, Movie.class, COLLECTION_NAME);
    }

}
