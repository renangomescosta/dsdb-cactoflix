package com.dsdb.cactoflix.service;

import com.dsdb.cactoflix.model.User;
import com.dsdb.cactoflix.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Optional;

@AllArgsConstructor
@Service
@Profile("app")
public class UserService {
    private UserRepository userRepository;
    public User findUserByEmail(String email){
        Optional<User> response = userRepository.findUser(email);
        return response.orElse(null);
    }

}
