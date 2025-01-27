package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.GatewayProperties;
import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.common.Utils;
import in.org.projecteka.hiu.dataflow.model.GatewayDataFlowRequest;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;


import static in.org.projecteka.hiu.common.Constants.*;
import static in.org.projecteka.hiu.consent.ConsentException.failedToInitiateDataFlowRequest;
import static java.util.function.Predicate.not;
import static org.slf4j.LoggerFactory.getLogger;
import static reactor.core.publisher.Mono.error;

@AllArgsConstructor
public class DataFlowClient {
    private final WebClient.Builder webClientBuilder;
    private final GatewayProperties gatewayProperties;
    private final HiuProperties hiuProperties;
    private static final Logger logger = getLogger(DataFlowClient.class);

    public Mono<Void> initiateDataFlowRequest(GatewayDataFlowRequest dataFlowRequest, String token, String cmSuffix, String requestId) {
        return webClientBuilder.build()
                .post()
                .uri(gatewayProperties.getBaseUrl() + GATEWAY_PATH_HEALTH_INFORMATION_REQUEST)
                .header("Authorization", token)
                .header("X-CM-ID", cmSuffix)
                .header(CORRELATION_ID, MDC.get(CORRELATION_ID))
                .header(REQUEST_ID, requestId)
                .header(TIMESTAMP, Utils.getISOTimestamp())
                .header(X_HIU_ID, hiuProperties.getId())
                .body(Mono.just(dataFlowRequest), GatewayDataFlowRequest.class)
                .retrieve()
                .onStatus(not(HttpStatus::is2xxSuccessful),
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .doOnNext(logger::error)
                                .then(error(failedToInitiateDataFlowRequest())))
                .toBodilessEntity()
                .then();
    }
}
