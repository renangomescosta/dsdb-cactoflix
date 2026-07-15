package com.dsdb.cactoflix.model;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.context.annotation.Profile;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Rating {
    private int userId;
    private int movieId;
    private Integer rating; // pode ser Nulo => Se for nulo, não computa
}
