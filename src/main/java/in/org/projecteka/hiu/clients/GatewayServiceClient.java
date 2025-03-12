package in.org.projecteka.hiu.clients;

import in.org.projecteka.hiu.GatewayProperties;
import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.common.Gateway;
import in.org.projecteka.hiu.common.Utils;
import in.org.projecteka.hiu.consent.model.ConsentArtefactRequest;
import in.org.projecteka.hiu.consent.model.consentmanager.ConsentOnNotifyRequest;
import in.org.projecteka.hiu.consent.model.consentmanager.ConsentRequest;
import in.org.projecteka.hiu.patient.model.PatientStatusNotification;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static in.org.projecteka.hiu.common.Constants.*;
import static in.org.projecteka.hiu.consent.ConsentException.creationFailed;
import static java.time.Duration.ofMillis;
import static java.util.function.Predicate.not;
import static org.slf4j.LoggerFactory.getLogger;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static reactor.core.publisher.Mono.error;
import static reactor.core.publisher.Mono.just;

public class GatewayServiceClient {


    private final WebClient webClient;
    private final GatewayProperties gatewayProperties;
    private final Gateway gateway;

    private final HiuProperties hiuProperties;
    private static final Logger logger = getLogger(GatewayServiceClient.class);


    public GatewayServiceClient(WebClient.Builder webClient,
                                GatewayProperties gatewayProperties,
                                Gateway gateway,
                                HiuProperties hiuProperties) {
        this.webClient = webClient.baseUrl(gatewayProperties.getBaseUrl()).build();
        this.gatewayProperties = gatewayProperties;
        this.gateway = gateway;
        this.hiuProperties = hiuProperties;
    }

    public Mono<Void> sendConsentRequest(String cmSuffix, ConsentRequest request, String requestId) {
        return gateway.token()
                .flatMap(token -> webClient
                        .post()
                        .uri(GATEWAY_PATH_CONSENT_REQUESTS_INIT)
                        .header(AUTHORIZATION, token)
                        .header(X_CM_ID, cmSuffix)
                        .header(CORRELATION_ID, MDC.get(CORRELATION_ID))
                        .header(REQUEST_ID, requestId)
                        .header(TIMESTAMP, Utils.getISOTimestamp())
                        .body(just(request), ConsentRequest.class)
                        .retrieve()
                        .onStatus(not(HttpStatus::is2xxSuccessful),
                                clientResponse -> clientResponse.bodyToMono(String.class)
                                        .doOnNext(logger::error)
                                        .then(error(creationFailed())))
                        .toBodilessEntity()
                        .timeout(ofMillis(gatewayProperties.getRequestTimeout())))
                .then();
    }

    public Mono<Void> requestConsentArtefact(ConsentArtefactRequest request, String cmSuffix, UUID requestId) {
        return gateway.token()
                .flatMap(token -> webClient
                        .post()
                        .uri(GATEWAY_PATH_CONSENT_ARTEFACT_FETCH)
                        .header(AUTHORIZATION, token)
                        .header(X_CM_ID, cmSuffix)
                        .header(CORRELATION_ID, MDC.get(CORRELATION_ID))
                        .header(REQUEST_ID, requestId.toString())
                        .header(TIMESTAMP, Utils.getISOTimestamp())
                        .header(X_HIU_ID, hiuProperties.getId())
                        .body(just(request), ConsentArtefactRequest.class)
                        .retrieve()
                        .onStatus(not(HttpStatus::is2xxSuccessful), clientResponse -> error(creationFailed()))
                        .toBodilessEntity()
                        .timeout(ofMillis(gatewayProperties.getRequestTimeout())))
                .then();
    }

    public Mono<Void> sendConsentOnNotify(String cmSuffix, ConsentOnNotifyRequest request) {
        return gateway.token()
                .flatMap(token -> webClient
                        .post()
                        .uri(GATEWAY_PATH_CONSENT_ON_NOTIFY)
                        .header(AUTHORIZATION, token)
                        .header(X_CM_ID, cmSuffix)
                        .header(CORRELATION_ID, MDC.get(CORRELATION_ID))
                        .header(REQUEST_ID, UUID.randomUUID().toString())
                        .header(TIMESTAMP, Utils.getISOTimestamp())
                        .body(just(request), ConsentOnNotifyRequest.class)
                        .retrieve()
                        .onStatus(not(HttpStatus::is2xxSuccessful),
                                clientResponse -> clientResponse.bodyToMono(String.class)
                                        .doOnNext(logger::error)
                                        .then(error(creationFailed())))
                        .toBodilessEntity()
                        .timeout(ofMillis(gatewayProperties.getRequestTimeout())))
                .then();
    }

    public Mono<Void> sendPatientStatusOnNotify(String cmSuffix, PatientStatusNotification request) {
        return gateway.token()
                .flatMap(token -> webClient
                        .post()
                        .uri(PATH_PATIENT_STATUS_ON_NOTIFY)
                        .header(AUTHORIZATION, token)
                        .header(X_CM_ID, cmSuffix)
                        .header(CORRELATION_ID, MDC.get(CORRELATION_ID))
                        .body(just(request), PatientStatusNotification.class)
                        .retrieve()
                        .onStatus(not(HttpStatus::is2xxSuccessful),
                                clientResponse -> clientResponse.bodyToMono(String.class)
                                        .doOnNext(logger::error)
                                        .then(error(creationFailed())))
                        .toBodilessEntity()
                        .timeout(ofMillis(gatewayProperties.getRequestTimeout())))
                .then();
    }
}
