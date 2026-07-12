package com.dsdb.cactoflix.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Motor de recomendação SVD (fold-in) em Java puro.
 *
 * Carrega UMA VEZ, no startup, os artefatos exportados do modelo treinado em
 * Python (recommendation/svd_model.json, gerado por tools/export_svd_model.py)
 *
 * Esta classe NÃO conhece HTTP nem banco de dados: recebe notas, devolve
 * (movieId, score). Quem busca as notas no Mongo é o RecommendationService.
 */
@Component
public class SvdRecommender {

    /** Mínimo de avaliações válidas para o fold-in (abaixo disso: fallback). */
    public static final int MIN_VALID_RATINGS = 3;

    /** Par (filme, score previsto) devolvido pelo motor. */
    public record ScoredMovie(long movieId, double score) {}

    /** Estrutura do svd_model.json (preenchida pelo Jackson). */
    private static class ModelData {
        public double globalMean;
        public int nFactors;
        public double regularization;
        public long[] itemIds;
        public double[] bi;
        public double[][] qi;
    }

    private final double mu;
    private final int nFactors;
    private final double lambda;
    private final long[] itemIds;
    private final double[] bi;
    private final double[][] qi;
    private final Map<Long, Integer> rawToInner;

    public SvdRecommender() {
        ModelData data;
        try (InputStream in = new ClassPathResource("recommendation/svd_model.json").getInputStream()) {
            data = new ObjectMapper().readValue(in, ModelData.class);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Não foi possível carregar recommendation/svd_model.json do classpath. "
                    + "Gere-o com tools/export_svd_model.py e coloque em src/main/resources/recommendation/.", e);
        }
        this.mu = data.globalMean;
        this.nFactors = data.nFactors;
        this.lambda = data.regularization;
        this.itemIds = data.itemIds;
        this.bi = data.bi;
        this.qi = data.qi;
        this.rawToInner = new HashMap<>(itemIds.length * 2);
        for (int inner = 0; inner < itemIds.length; inner++) {
            rawToInner.put(itemIds[inner], inner);
        }
    }

    /** Média global do treino (usada pelo service como referência, se precisar). */
    public double globalMean() {
        return mu;
    }

    /**
     * Recomenda até k filmes a partir das notas do usuário.
     *
     * @param ratedItems mapa movieId -> nota (1..5). Notas de filmes que o
     *                   modelo não conhece são ignoradas silenciosamente.
     * @return top-k (movieId, score) em ordem decrescente, excluindo os filmes
     *         já avaliados; lista VAZIA se houver menos de
     *         {@link #MIN_VALID_RATINGS} avaliações válidas (o chamador deve
     *         então usar o fallback de popularidade).
     */
    public List<ScoredMovie> recommend(Map<Long, Integer> ratedItems, int k) {
        // 1) Monta Qi (linhas = vetores dos filmes avaliados) e r ajustado
        List<double[]> rows = new ArrayList<>();
        List<Double> adjusted = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : ratedItems.entrySet()) {
            if (e.getValue() == null) continue;              // avaliação sem nota não computa
            Integer inner = rawToInner.get(e.getKey());
            if (inner == null) continue;                     // filme fora do modelo: ignora
            rows.add(qi[inner]);
            adjusted.add(e.getValue() - mu - bi[inner]);
        }
        if (rows.size() < MIN_VALID_RATINGS) {
            return List.of();
        }

        // 2) Fold-in: resolve (Qiᵀ·Qi + λI) · pu = Qiᵀ · r
        double[][] a = new double[nFactors][nFactors];
        double[] b = new double[nFactors];
        for (int i = 0; i < rows.size(); i++) {
            double[] q = rows.get(i);
            double r = adjusted.get(i);
            for (int x = 0; x < nFactors; x++) {
                b[x] += q[x] * r;
                for (int y = x; y < nFactors; y++) {
                    a[x][y] += q[x] * q[y];
                }
            }
        }
        for (int x = 0; x < nFactors; x++) {
            a[x][x] += lambda;
            for (int y = 0; y < x; y++) {
                a[x][y] = a[y][x];                           // espelha a parte simétrica
            }
        }
        double[] pu = solve(a, b);

        // 3) Score de todos os candidatos (exceto já avaliados) e top-k
        List<ScoredMovie> scored = new ArrayList<>(itemIds.length);
        for (int inner = 0; inner < itemIds.length; inner++) {
            long movieId = itemIds[inner];
            if (ratedItems.containsKey(movieId)) continue;   // não recomenda o que já avaliou
            scored.add(new ScoredMovie(movieId, mu + bi[inner] + dot(qi[inner], pu)));
        }
        scored.sort((p, q) -> Double.compare(q.score(), p.score()));
        return List.copyOf(scored.subList(0, Math.min(k, scored.size())));
    }

    private static double dot(double[] u, double[] v) {
        double s = 0.0;
        for (int i = 0; i < u.length; i++) s += u[i] * v[i];
        return s;
    }

    /** Resolve a·x = b por eliminação de Gauss com pivoteamento parcial (a e b são modificados). */
    private static double[] solve(double[][] a, double[] b) {
        int n = b.length;
        for (int col = 0; col < n; col++) {
            int piv = col;
            for (int r = col + 1; r < n; r++) {
                if (Math.abs(a[r][col]) > Math.abs(a[piv][col])) piv = r;
            }
            double[] tmpRow = a[col]; a[col] = a[piv]; a[piv] = tmpRow;
            double tmpB = b[col]; b[col] = b[piv]; b[piv] = tmpB;

            double d = a[col][col];
            for (int r = col + 1; r < n; r++) {
                double f = a[r][col] / d;
                if (f == 0.0) continue;
                for (int c = col; c < n; c++) a[r][c] -= f * a[col][c];
                b[r] -= f * b[col];
            }
        }
        double[] x = new double[n];
        for (int r = n - 1; r >= 0; r--) {
            double s = b[r];
            for (int c = r + 1; c < n; c++) s -= a[r][c] * x[c];
            x[r] = s / a[r][r];
        }
        return x;
    }
}
