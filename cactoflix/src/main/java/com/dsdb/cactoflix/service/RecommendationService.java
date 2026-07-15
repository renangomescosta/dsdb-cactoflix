package com.dsdb.cactoflix.service;

import com.dsdb.cactoflix.model.Movie;
import com.dsdb.cactoflix.model.Rating;
import com.dsdb.cactoflix.recommendation.RecommendedMovie;
import com.dsdb.cactoflix.recommendation.SvdRecommender;
import com.dsdb.cactoflix.repository.MovieRepository;
import com.dsdb.cactoflix.repository.RatingRepository;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lógica de negócio da recomendação. É o único ponto que liga a IA ao resto
 * do sistema, e segue a regra das camadas do projeto:
 *
 *   controller -> ESTE service -> repository (MongoDB)
 *                            \-> recommendation (motor SVD, sem banco)
 *
 * Responsabilidades:
 *  1. Buscar as notas do usuário na collection ratings (via RatingRepository).
 *  2. Chamar o motor SVD (fold-in) para obter o top-k de (movieId, score).
 *  3. Se não houver avaliações válidas suficientes (< 3), aplicar o fallback
 *     de POPULARIDADE calculado sobre a própria collection ratings.
 *  4. Cruzar os movieId com a collection movies para devolver os nomes.
 */
@AllArgsConstructor
@Service
@Profile("app")
public class RecommendationService {

    /** Amortecimento da média no fallback: filmes com poucas notas não dominam o ranking. */
    private static final double DAMPING = 10.0;

    private final RatingRepository ratingRepository;
    private final MovieRepository movieRepository;
    private final SvdRecommender recommender;

    /**
     * Recomendações para um usuário já cadastrado: as notas dele são lidas do
     * MongoDB. É o caminho GET /recommendations?userId=...
     */
    public List<RecommendedMovie> recommendForUser(int userId, int k) {
        List<Rating> ratings = ratingRepository.findByUser(userId);
        return recommend(toRatedMap(ratings), k);
    }

    /**
     * Recomendações de onboarding: o front envia as ~10 notas iniciais no corpo
     * da requisição (elas ainda não foram persistidas). É o caminho
     * POST /recommendations.
     */
    public List<RecommendedMovie> recommendFromRatings(List<Rating> ratings, int k) {
        return recommend(toRatedMap(ratings), k);
    }

    private Map<Long, Integer> toRatedMap(List<Rating> ratings) {
        Map<Long, Integer> rated = new HashMap<>();
        if (ratings == null) return rated;
        for (Rating r : ratings) {
            // nota null = avaliação sem nota: não computa (mesma regra do getNote)
            if (r != null && r.getRating() != null) {
                rated.put((long) r.getMovieId(), r.getRating());
            }
        }
        return rated;
    }

    private List<RecommendedMovie> recommend(Map<Long, Integer> rated, int k) {
        List<SvdRecommender.ScoredMovie> top = recommender.recommend(rated, k);
        if (top.isEmpty()) {
            // < 3 avaliações válidas (ou nenhum filme reconhecido pelo modelo)
            top = popularityFallback(rated, k);
        }
        return joinWithMovieNames(top);
    }

    /**
     * Fallback quando o fold-in não é confiável: ranqueia por média bayesiana
     * (média amortecida pela média global), calculada sobre TODAS as notas da
     * collection ratings. Exclui filmes que o usuário já avaliou.
     */
    private List<SvdRecommender.ScoredMovie> popularityFallback(Map<Long, Integer> exclude, int k) {
        List<Rating> all = ratingRepository.fetchData();

        Map<Long, long[]> agg = new HashMap<>(); // movieId -> [quantidade, soma]
        long count = 0;
        long total = 0;
        for (Rating r : all) {
            if (r.getRating() == null) continue;
            long[] acc = agg.computeIfAbsent((long) r.getMovieId(), x -> new long[2]);
            acc[0]++;
            acc[1] += r.getRating();
            count++;
            total += r.getRating();
        }
        if (count == 0) return List.of();

        double globalAvg = (double) total / count;
        List<SvdRecommender.ScoredMovie> ranked = new ArrayList<>(agg.size());
        for (Map.Entry<Long, long[]> e : agg.entrySet()) {
            if (exclude.containsKey(e.getKey())) continue;
            long n = e.getValue()[0];
            long sum = e.getValue()[1];
            double score = (sum + DAMPING * globalAvg) / (n + DAMPING);
            ranked.add(new SvdRecommender.ScoredMovie(e.getKey(), score));
        }
        ranked.sort((p, q) -> Double.compare(q.score(), p.score()));
        return ranked.subList(0, Math.min(k, ranked.size()));
    }

    /** Cruza os movieId com a collection movies para obter os nomes. */
    private List<RecommendedMovie> joinWithMovieNames(List<SvdRecommender.ScoredMovie> top) {
        Map<Long, String> names = new HashMap<>();
        for (Movie m : movieRepository.fetchData()) {
            names.put(m.getId(), m.getName());
        }
        List<RecommendedMovie> out = new ArrayList<>(top.size());
        for (SvdRecommender.ScoredMovie s : top) {
            out.add(new RecommendedMovie(s.movieId(), names.get(s.movieId()), s.score()));
        }
        return out;
    }
}
