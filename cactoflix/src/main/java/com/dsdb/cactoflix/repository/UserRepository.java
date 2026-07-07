package com.dsdb.cactoflix.repository;
import com.dsdb.cactoflix.model.User;
import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


// Repository no String Boot => Entrega o mesmo objeto pra todos => Singleton
@Repository
public class UserRepository {
    //TODO: Aqui entra o banco de dados
    static final String DATABASE_URL = "preencher";

    private final List<User> mockedData = new ArrayList<>(List.of(
            new User(1L,"Renan","renan@gmail.com"),
            new User(2L,"Derick","derick@gmail.com"),
            new User(3L,"Becky","becky@gmail.com"),
            new User(4L,"Dhener","dhener@gmail.com"),
            new User(5L,"Alisson","alisson@gmail.com")
    ));
    public List<User> fetchData(){
        //TODO: Aqui entra o banco de dados
        return mockedData;
    }

    public Optional<User> findUser(String email){
        List<User> data = fetchData();
        return data.stream()
            .filter(user -> user.getEmail().equals(email))
            .findFirst();
    }

}
