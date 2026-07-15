package com.dsdb.cactoflix.controller;

import com.dsdb.cactoflix.model.Rating;
import com.dsdb.cactoflix.recommendation.RecommendedMovie;
import com.dsdb.cactoflix.service.RecommendationService;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("/recommendations")
@Profile("app")
public class RecommendationController {

    public record OnboardingRequest(List<Rating> ratings) {}

    private final RecommendationService recommendationService;

    @GetMapping
    public List<RecommendedMovie> forUser(
            @RequestParam int userId,
            @RequestParam(defaultValue = "10") int k) {
        return recommendationService.recommendForUser(userId, k);
    }

    @PostMapping
    public List<RecommendedMovie> onboarding(
            @RequestBody OnboardingRequest request,
            @RequestParam(defaultValue = "10") int k) {
        return recommendationService.recommendFromRatings(request.ratings(), k);
    }
}
