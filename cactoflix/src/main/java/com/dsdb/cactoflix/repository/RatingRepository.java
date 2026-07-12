package com.dsdb.cactoflix.repository;

import com.dsdb.cactoflix.model.Rating;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

// import javax.swing.text.html.Option;
// import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Repository no Spring Boot => Entrega o mesmo objeto pra todos => Singleton
@Repository
public class RatingRepository {

    private static final String COLLECTION_NAME = "ratings";

    private final MongoTemplate mongoTemplate;

    @Autowired
    public RatingRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<Rating> fetchData() {
        return mongoTemplate.findAll(Rating.class, COLLECTION_NAME);
    }
    public void save(Rating rating){
        mongoTemplate.insert(rating, COLLECTION_NAME);
        return;
    }

    // atualiza o rate
    public void update(Rating rating) {
        mongoTemplate.save(rating, COLLECTION_NAME);
    }

    public List<Rating> findByUser(int userId) {
        Query query = new Query(Criteria.where("userId").is(userId));
        return mongoTemplate.find(query, Rating.class, COLLECTION_NAME);
    }

    public Optional<Rating> findByUserAndMovie(int userId, int movieId){
        Query query = new Query(
                Criteria.where("userId").is(userId)
                        .and("movieId").is(movieId)
        );

        Rating rating = mongoTemplate.findOne(query, Rating.class, COLLECTION_NAME);

        return Optional.ofNullable(rating);
    }
}