package com.dsdb.cactoflix.infrastructure.loadBalancer;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoadBalancerControllerTest {

    // Simulando URL
    private static final String BACKENDS_PROPERTY = "http://app1:8080,http://app2:8080,http://app3:8080";
    private static final List<String> BACKENDS = List.of(BACKENDS_PROPERTY.split(","));

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private HttpServletRequest request;

    private LoadBalancerController controller;

    @BeforeEach
    void setUp() {
        controller = new LoadBalancerController(BACKENDS_PROPERTY);
        ReflectionTestUtils.setField(controller, "restTemplate", restTemplate);
        // Por padrão, nos testes, todo backend está "de pé" (sem abrir socket de verdade).
        setHealthCheck(backend -> true);
    }

    private void setHealthCheck(Predicate<String> healthCheck) {
        ReflectionTestUtils.setField(controller, "backendHealthCheck", healthCheck);
    }

    private static int primaryIndexFor(String email) {
        return Math.floorMod(email.hashCode(), BACKENDS.size());
    }

    @Test
    void constructorSplitsBackendsPropertyByComma() {
        assertThat(ReflectionTestUtils.getField(controller, "backends")).isEqualTo(BACKENDS);
    }

    @Test
    void missingEmailHeaderReturnsBadRequestWithoutForwarding() {
        when(request.getHeader("X-Client-Email")).thenReturn(null);

        ResponseEntity<String> response = controller.forward(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo("Header X-Client-Email é obrigatório");
        verify(restTemplate, never()).getForEntity(anyString(), eq(String.class));
    }

    @Test
    void blankEmailHeaderReturnsBadRequestWithoutForwarding() {
        when(request.getHeader("X-Client-Email")).thenReturn("   ");

        ResponseEntity<String> response = controller.forward(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(restTemplate, never()).getForEntity(anyString(), eq(String.class));
    }

    @Test
    void forwardsToDeterministicBackendBasedOnEmailHash() {
        String email = "cliente@example.com";
        when(request.getHeader("X-Client-Email")).thenReturn(email);
        when(request.getRequestURI()).thenReturn("/movies");
        when(request.getQueryString()).thenReturn(null);

        ResponseEntity<String> canned = ResponseEntity.ok("body");
        when(restTemplate.getForEntity(anyString(), eq(String.class))).thenReturn(canned);

        ResponseEntity<String> response = controller.forward(request);

        String expectedUrl = BACKENDS.get(primaryIndexFor(email)) + "/movies";

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).getForEntity(urlCaptor.capture(), eq(String.class));
        assertThat(urlCaptor.getValue()).isEqualTo(expectedUrl);
        assertThat(response).isSameAs(canned);
    }

    @Test
    void appendsQueryStringWhenPresent() {
        when(request.getHeader("X-Client-Email")).thenReturn("cliente@example.com");
        when(request.getRequestURI()).thenReturn("/movies");
        when(request.getQueryString()).thenReturn("genre=Comedy");
        when(restTemplate.getForEntity(anyString(), eq(String.class))).thenReturn(ResponseEntity.ok("body"));

        controller.forward(request);

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).getForEntity(urlCaptor.capture(), eq(String.class));
        assertThat(urlCaptor.getValue()).endsWith("/movies?genre=Comedy");
    }

    @Test
    void sameEmailAlwaysRoutesToTheSameBackend() {
        String email = "sempre-o-mesmo@example.com";
        when(request.getHeader("X-Client-Email")).thenReturn(email);
        when(request.getRequestURI()).thenReturn("/movies");
        when(request.getQueryString()).thenReturn(null);
        when(restTemplate.getForEntity(anyString(), eq(String.class))).thenReturn(ResponseEntity.ok("body"));

        controller.forward(request);
        controller.forward(request);

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate, org.mockito.Mockito.times(2)).getForEntity(urlCaptor.capture(), eq(String.class));
        List<String> calledUrls = urlCaptor.getAllValues();
        assertThat(calledUrls.get(0)).isEqualTo(calledUrls.get(1));
    }

    @Test
    void fallsBackToNextBackendWhenPrimaryIsDown() {
        String email = "cliente@example.com";
        String primaryBackend = BACKENDS.get(primaryIndexFor(email));
        String expectedFallback = BACKENDS.get(Math.floorMod(primaryIndexFor(email) + 1, BACKENDS.size()));

        // Só o backend primário está fora do ar; os demais estão de pé.
        setHealthCheck(backend -> !backend.equals(primaryBackend));

        when(request.getHeader("X-Client-Email")).thenReturn(email);
        when(request.getRequestURI()).thenReturn("/movies");
        when(request.getQueryString()).thenReturn(null);
        when(restTemplate.getForEntity(anyString(), eq(String.class))).thenReturn(ResponseEntity.ok("body"));

        controller.forward(request);

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).getForEntity(urlCaptor.capture(), eq(String.class));
        assertThat(urlCaptor.getValue()).isEqualTo(expectedFallback + "/movies");
    }

    @Test
    void returnsInternalServerErrorWhenAllBackendsAreDown() {
        setHealthCheck(backend -> false);

        when(request.getHeader("X-Client-Email")).thenReturn("cliente@example.com");

        ResponseEntity<String> response = controller.forward(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isEqualTo("Todos os servidores estão desligados!");
        verify(restTemplate, never()).getForEntity(anyString(), eq(String.class));
    }

    @Test
    void triesEveryBackendExactlyOnceBeforeGivingUp() {
        java.util.List<String> attempted = new java.util.ArrayList<>();
        setHealthCheck(backend -> {
            attempted.add(backend);
            return false;
        });

        when(request.getHeader("X-Client-Email")).thenReturn("cliente@example.com");

        controller.forward(request);

        assertThat(attempted).hasSize(BACKENDS.size());
        assertThat(Set.copyOf(attempted)).isEqualTo(Set.copyOf(BACKENDS));
    }
}
