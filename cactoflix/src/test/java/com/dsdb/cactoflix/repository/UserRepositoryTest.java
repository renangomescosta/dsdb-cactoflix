package com.dsdb.cactoflix.repository;

import com.dsdb.cactoflix.model.User;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRepositoryTest {

    private static final String COLLECTION_NAME = "users";

    @Mock
    private MongoTemplate mongoTemplate;

    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository = new UserRepository(mongoTemplate);
    }

    @Test
    void fetchDataUsesUsersCollection() {
        List<User> expected = List.of(new User(1L, "Ana", "ana@example.com"));
        when(mongoTemplate.findAll(User.class, COLLECTION_NAME)).thenReturn(expected);

        assertThat(userRepository.fetchData()).isSameAs(expected);
    }

    @Test
    void findUserReturnsPresentOptionalWhenFound() {
        User user = new User(1L, "Ana", "ana@example.com");
        when(mongoTemplate.findOne(any(Query.class), eq(User.class), eq(COLLECTION_NAME))).thenReturn(user);

        Optional<User> result = userRepository.findUser("ana@example.com");

        assertThat(result).contains(user);
    }

    @Test
    void findUserReturnsEmptyOptionalWhenNotFound() {
        when(mongoTemplate.findOne(any(Query.class), eq(User.class), eq(COLLECTION_NAME))).thenReturn(null);

        Optional<User> result = userRepository.findUser("missing@example.com");

        assertThat(result).isEmpty();
    }

    @Test
    void findUserBuildsEmailCriteriaAgainstUsersCollection() {
        when(mongoTemplate.findOne(any(Query.class), eq(User.class), eq(COLLECTION_NAME))).thenReturn(null);

        userRepository.findUser("ana@example.com");

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findOne(captor.capture(), eq(User.class), eq(COLLECTION_NAME));
        Document queryObject = captor.getValue().getQueryObject();
        assertThat(queryObject.get("email")).isEqualTo("ana@example.com");
    }
}
