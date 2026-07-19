package com.dsdb.cactoflix.controller;

import com.dsdb.cactoflix.model.Rating;
import com.dsdb.cactoflix.service.RatingService;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@AllArgsConstructor
@RestController
@RequestMapping("/ratings")
@Profile("app")
public class RatingController {
    private final RatingService ratingService;

    @GetMapping
    public List<Rating> getRatings(){
        return ratingService.getRatings();
    }
    @PostMapping
    public void publish(@RequestBody Rating rating){
        ratingService.publishRating(rating);
    }
}
