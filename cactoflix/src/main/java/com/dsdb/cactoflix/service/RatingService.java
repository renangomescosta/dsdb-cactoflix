package com.dsdb.cactoflix.service;

import com.dsdb.cactoflix.model.Rating;
import com.dsdb.cactoflix.repository.RatingRepository;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@AllArgsConstructor
@Service
@Profile("app")
public class RatingService {
    private final RatingRepository ratingRepository;

    public void publishRating(Rating rating) {

        if(ratingRepository.findByUserAndMovie(rating.getUserId(),rating.getMovieId()).isPresent()){
            ratingRepository.update(rating);
        } else{
            ratingRepository.save(rating);
        }
    }

    public List<Rating> getRatings() {
        return ratingRepository.fetchData();
    }
}