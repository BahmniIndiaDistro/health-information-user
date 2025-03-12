package in.org.projecteka.hiu.clients;

import in.org.projecteka.hiu.GatewayProperties;
import in.org.projecteka.hiu.common.Gateway;
import in.org.projecteka.hiu.common.Utils;
import in.org.projecteka.hiu.patient.model.AbhaAddressSearchResponse;
import in.org.projecteka.hiu.patient.model.FindPatientRequest;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static in.org.projecteka.hiu.clients.PatientSearchThrowable.notFound;
import static in.org.projecteka.hiu.clients.PatientSearchThrowable.unknown;
import static in.org.projecteka.hiu.common.Constants.*;
import static java.time.Duration.ofMillis;
import static java.util.function.Predicate.not;
import static org.slf4j.LoggerFactory.getLogger;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpStatus.*;
import static reactor.core.publisher.Mono.error;
import static reactor.core.publisher.Mono.just;

public class AbhaAddressServiceClient {


    private final WebClient webClient;
    private final GatewayProperties gatewayProperties;
    private final Gateway gateway;
    private static final Logger logger = getLogger(AbhaAddressServiceClient.class);


    public AbhaAddressServiceClient(WebClient.Builder webClientBuilder,
                                    GatewayProperties gatewayProperties,
                                    Gateway gateway) {
        this.webClient = webClientBuilder.baseUrl(gatewayProperties.getAbhaAddressBaseUrl()).build();
        this.gatewayProperties = gatewayProperties;
        this.gateway = gateway;
    }

    public Mono<AbhaAddressSearchResponse> findPatientWith(FindPatientRequest request, String cmSuffix) {
        return gateway.token()
                .flatMap(token -> webClient.
                        post()
                        .uri(PATH_ABHA_ADDRESS_SEARCH)
                        .header(AUTHORIZATION, token)
                        .header(REQUEST_ID, UUID.randomUUID().toString())
                        .header(TIMESTAMP, Utils.getISOTimestamp())
                        .body(just(request), FindPatientRequest.class)
                        .retrieve()
                        .onStatus(httpStatus -> httpStatus == BAD_REQUEST, clientResponse -> error(notFound()))
                        .onStatus(not(HttpStatus::is2xxSuccessful), clientResponse -> error(unknown()))
                        .bodyToMono(AbhaAddressSearchResponse.class)
                        .timeout(ofMillis(gatewayProperties.getRequestTimeout())));
    }

}
