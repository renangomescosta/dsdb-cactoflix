package com.dsdb.cactoflix.repository;


import com.dsdb.cactoflix.model.Movie;
import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.List;


// Repository no String Boot => Entrega o mesmo objeto pra todos => Singleton
@Repository
public class MovieRepository {
    //TODO: Aqui entra o banco de dados
    static final String DATABASE_URL = "preencher";

    private final List<Movie> mockedData = new ArrayList<>(List.of(
            new Movie(1L, "Toy Story 1", List.of("Ação","Aventura")),
            new Movie(2L, "Vingadores", List.of("Ação","Romance")),
            new Movie(3L, "Avatar", List.of("Romance","Terror","Ação")),
            new Movie(4L, "Titanic", List.of("Romance")),
            new Movie(5L, "Tá Chovendo Hambúrguer", List.of("Terror"))
    ));
    public List<Movie> fetchData(){
        //TODO: Aqui entra o banco de dados
        return mockedData;
    }

}
