package com.dsdb.cactoflix.controller;


import com.dsdb.cactoflix.model.User;

import com.dsdb.cactoflix.service.UserService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@AllArgsConstructor
@RestController
@RequestMapping("/users")
public class UserController {
    private final UserService userService;

    @GetMapping
    public ResponseEntity<User>  findUser(@RequestParam String email){
        User user =  userService.findUserByEmail(email);
        if (user == null){
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(user);
    }

}
