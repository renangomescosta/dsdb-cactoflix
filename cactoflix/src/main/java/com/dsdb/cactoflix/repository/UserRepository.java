package com.dsdb.cactoflix.repository;

import com.dsdb.cactoflix.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

// import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


// Repository no String Boot => Entrega o mesmo objeto pra todos => Singleton
@Repository
@Profile("app")
public class UserRepository {

    private static final String COLLECTION_NAME = "users";

    private final MongoTemplate mongoTemplate;

    @Autowired
    public UserRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<User> fetchData() {
        return mongoTemplate.findAll(User.class, COLLECTION_NAME);
    }

    // Procura o usuário por email
    public Optional<User> findUser(String email) {
        Query query = new Query(Criteria.where("email").is(email));

        User user = mongoTemplate.findOne(query, User.class, COLLECTION_NAME);

        return Optional.ofNullable(user);
    }
}