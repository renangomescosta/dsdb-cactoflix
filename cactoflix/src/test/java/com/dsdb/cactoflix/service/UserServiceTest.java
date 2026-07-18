package com.dsdb.cactoflix.service;

import com.dsdb.cactoflix.model.User;
import com.dsdb.cactoflix.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository);
    }

    @Test
    void findUserByEmailReturnsUserWhenPresent() {
        User user = new User(1L, "Ana", "ana@example.com");
        when(userRepository.findUser("ana@example.com")).thenReturn(Optional.of(user));

        User result = userService.findUserByEmail("ana@example.com");

        assertThat(result).isSameAs(user);
    }

    @Test
    void findUserByEmailReturnsNullWhenAbsent() {
        when(userRepository.findUser("missing@example.com")).thenReturn(Optional.empty());

        User result = userService.findUserByEmail("missing@example.com");

        assertThat(result).isNull();
    }
}
