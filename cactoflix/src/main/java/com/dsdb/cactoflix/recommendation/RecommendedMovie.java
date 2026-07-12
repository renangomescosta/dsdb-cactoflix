package com.dsdb.cactoflix.recommendation;

/**
 * Item da resposta do endpoint de recomendação: o movieId, o nome vindo da
 * collection movies do MongoDB e o score previsto pelo modelo (ou pela
 * popularidade, no caso de fallback).
 */
public record RecommendedMovie(long movieId, String name, double score) {}
