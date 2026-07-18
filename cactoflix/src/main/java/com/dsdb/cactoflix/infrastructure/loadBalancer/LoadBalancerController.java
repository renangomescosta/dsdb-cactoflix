package com.dsdb.cactoflix.infrastructure.loadBalancer;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.util.List;
import java.util.function.Predicate;

@RestController
@Profile("lb")
public class LoadBalancerController {
    // Rotas: um endereço por back-end, ex.: "http://192.168.0.111:8080,http://192.168.0.112:8080"
    private final List<String> backends;
    private final RestTemplate restTemplate = new RestTemplate();

    // Predicate => Interface Java que devolve True ou False => Roda IsBackendup => Já ve se ja ta True/False
    private final Predicate<String> backendHealthCheck = this::isBackendUp;

    public LoadBalancerController(
            @Value("${loadbalancer.backends:http://app1:8080,http://app2:8080,http://app3:8080}") String backendsProperty) {
        this.backends = List.of(backendsProperty.split(","));
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
        String backend = fetchBackend(email);
        if (backend == null){
            return ResponseEntity
                    .internalServerError()
                    .body("Todos os servidores estão desligados!");
        }

        String url = backend + request.getRequestURI() +
                (request.getQueryString() != null ? "?" + request.getQueryString() : "");

        return restTemplate.getForEntity(url, String.class);
    }
    // Escolhe o Back-End c/ base numa chave => emailDoCliente |  roteamento por chave
    private String fetchBackend(String email) {
        int index;
        String candidate;

        for (int displacement = 0; displacement < backends.size(); displacement++) {
            index = Math.floorMod(email.hashCode() + displacement, backends.size());
            candidate = backends.get(index);
            if (backendHealthCheck.test(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isBackendUp(String backend) {
        try {
            URI uri = URI.create(backend);
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(uri.getHost(), uri.getPort()), 1000); // 1s
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

}