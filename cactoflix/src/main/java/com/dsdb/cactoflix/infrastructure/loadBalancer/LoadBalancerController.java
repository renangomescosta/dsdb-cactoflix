package com.dsdb.cactoflix.infrastructure.loadBalancer;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@Profile("lb")
public class LoadBalancerController {
    // Rotas:
    private final List<String> backends = List.of(
            "http://app1:8080",
            "http://app2:8080",
            "http://app3:8080"
    );

    private final RestTemplate restTemplate = new RestTemplate();

    // Escolhe o Back-End c/ base numa chave => emailDoCliente |  roteamento por chave
    private String nextBackend(String email) {
        int index = Math.abs(email.hashCode() % backends.size());
        return backends.get(index);
    }

    // Encaminha TODAS as reqs. que chegarem
    @RequestMapping("/**")
    public ResponseEntity<String> forward(HttpServletRequest request) {
        String email = request.getHeader("X-Client-Email");
        if(email == null || email.isBlank()){
            return ResponseEntity
                    .badRequest()
                    .body("Header X-Client-Email é obrigatório");
        }
        String backend = nextBackend(email);
        // Mantém a rota, só pega o próximo BackEnd
        String url = backend + request.getRequestURI() +
                (request.getQueryString() != null ? "?" + request.getQueryString() : "");

        return restTemplate.getForEntity(url, String.class);
    }
}