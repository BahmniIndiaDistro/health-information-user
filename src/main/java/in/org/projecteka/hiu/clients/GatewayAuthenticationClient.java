package in.org.projecteka.hiu.clients;

import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.common.Constants;
import in.org.projecteka.hiu.common.Utils;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Properties;
import java.util.UUID;

import static in.org.projecteka.hiu.common.Constants.*;
import static java.lang.String.format;

@AllArgsConstructor
public class GatewayAuthenticationClient {
    private final WebClient webclient;
    private final Logger logger = LogManager.getLogger(GatewayAuthenticationClient.class);

    public GatewayAuthenticationClient(WebClient.Builder webClient, String baseUrl) {
        this.webclient = webClient.baseUrl(baseUrl).build();
    }

    public Mono<Token> getTokenFor(String clientId, String clientSecret) {
        return webclient
                .post()
                .uri(Constants.PATH_GATEWAY_SESSION)
                .contentType(MediaType.APPLICATION_JSON)
                .header(CORRELATION_ID, MDC.get(CORRELATION_ID))
                .header(REQUEST_ID, UUID.randomUUID().toString())
                .header(TIMESTAMP, Utils.getISOTimestamp())
                .header(X_CM_ID,"sbx")
                .accept(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(requestWith(clientId, clientSecret)))
                .retrieve()
                .onStatus(HttpStatus::isError, clientResponse -> clientResponse.bodyToMono(String.class)
                        .doOnNext(logger::error)
                        .thenReturn(ClientError.authenticationFailed()))
                .bodyToMono(Properties.class)
                .map(properties -> new Token(format("Bearer %s", properties.getProperty("accessToken"))));
    }

    private SessionRequest requestWith(String clientId, String clientSecret) {
        return new SessionRequest(clientId, clientSecret);
    }

    @AllArgsConstructor
    @Data
    private static class SessionRequest {
        private String clientId;
        private String clientSecret;
        private final String grantType = "client_credentials";
    }
}
