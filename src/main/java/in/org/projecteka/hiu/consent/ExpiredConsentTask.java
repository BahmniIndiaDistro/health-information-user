package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.clients.GatewayServiceClient;
import in.org.projecteka.hiu.consent.model.ConsentArtefact;
import in.org.projecteka.hiu.consent.model.ConsentArtefactReference;
import in.org.projecteka.hiu.consent.model.ConsentNotification;
import in.org.projecteka.hiu.consent.model.ConsentStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static in.org.projecteka.hiu.ClientError.consentArtefactNotFound;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.EXPIRED;

public class ExpiredConsentTask extends ConsentTask {
    private static final String CONSENT_EXPIRY_MESSAGE = "Consent is expired";
    private final DataFlowDeletePublisher dataFlowDeletePublisher;
    private final GatewayServiceClient gatewayServiceClient;

    public ExpiredConsentTask(ConsentRepository consentRepository, DataFlowDeletePublisher dataFlowDeletePublisher, GatewayServiceClient gatewayServiceClient) {
        super(consentRepository);
        this.dataFlowDeletePublisher = dataFlowDeletePublisher;
        this.gatewayServiceClient = gatewayServiceClient;
    }

    private Mono<Void> processArtefactReference(ConsentArtefactReference reference, String consentRequestId, LocalDateTime timestamp) {
        return consentRepository.updateStatus(reference, ConsentStatus.EXPIRED, timestamp)
                .then(dataFlowDeletePublisher.broadcastConsentExpiry(reference.getId(), consentRequestId));
    }


    @Override
    public Mono<Void> perform(ConsentNotification consentNotification, LocalDateTime timeStamp, UUID requestID) {
        if (consentNotification.getConsentArtefacts().isEmpty()) {
            return processNotificationRequest(consentNotification.getConsentRequestId(), EXPIRED);
        }
        return validateConsents(consentNotification.getConsentArtefacts())
                .flatMap(consentArtefacts -> {
                    var cmSuffix = getCmSuffixFromArtefact(consentArtefacts);
                    return gatewayServiceClient.sendConsentOnNotify(cmSuffix, buildConsentOnNotifyRequest(consentArtefacts, requestID));
                })
                .then(Mono.defer(() -> Flux.fromIterable(consentNotification.getConsentArtefacts())
                        .flatMap(reference -> processArtefactReference(reference,
                                consentNotification.getConsentRequestId(), timeStamp))
                        .then()));
    }


    private Mono<List<ConsentArtefact>> validateConsents(List<ConsentArtefactReference> consentArtefacts) {
        return Flux.fromIterable(consentArtefacts)
                .flatMap(consentArtefact -> consentRepository.getConsent(consentArtefact.getId(), ConsentStatus.GRANTED)
                        .switchIfEmpty(Mono.error(consentArtefactNotFound())))
                .collectList();
    }
}
