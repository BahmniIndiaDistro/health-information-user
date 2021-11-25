package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.clients.GatewayServiceClient;
import in.org.projecteka.hiu.common.GatewayResponse;
import in.org.projecteka.hiu.consent.model.ConsentArtefact;
import in.org.projecteka.hiu.consent.model.ConsentArtefactReference;
import in.org.projecteka.hiu.consent.model.ConsentNotification;
import in.org.projecteka.hiu.consent.model.ConsentStatus;
import in.org.projecteka.hiu.consent.model.consentmanager.ConsentAcknowledgement;
import in.org.projecteka.hiu.consent.model.consentmanager.ConsentOnNotifyRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static in.org.projecteka.hiu.ClientError.consentArtefactNotFound;
import static in.org.projecteka.hiu.common.Constants.IST;
import static in.org.projecteka.hiu.common.Constants.getCmSuffix;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.EXPIRED;
import static in.org.projecteka.hiu.consent.model.consentmanager.ConsentAcknowledgementStatus.OK;

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

    private ConsentOnNotifyRequest buildConsentOnNotifyRequest(List<ConsentArtefact> consentArtefacts, UUID requestID) {
        var requestId = UUID.randomUUID();
        var consentArtefactRequest = ConsentOnNotifyRequest
                .builder()
                .timestamp(LocalDateTime.now(ZoneId.of(IST)))
                .requestId(requestId);
        var acknowledgements = new ArrayList<ConsentAcknowledgement>();

        for (ConsentArtefact consentArtefact : consentArtefacts) {
            acknowledgements.add(ConsentAcknowledgement.builder().consentId(consentArtefact.getConsentId()).status(OK).build());
        }

        GatewayResponse gatewayResponse = new GatewayResponse(requestID.toString());
        consentArtefactRequest.resp(gatewayResponse).build();
        return consentArtefactRequest.acknowledgement(acknowledgements).build();
    }

    private String getCmSuffixFromArtefact(List<ConsentArtefact> consentArtefacts) {
        ConsentArtefact consentArtefact = consentArtefacts.get(0);
        return getCmSuffix(consentArtefact.getPatient().getId());
    }


    private Mono<List<ConsentArtefact>> validateConsents(List<ConsentArtefactReference> consentArtefacts) {
        return Flux.fromIterable(consentArtefacts)
                .flatMap(consentArtefact -> consentRepository.getConsent(consentArtefact.getId(), ConsentStatus.GRANTED)
                        .switchIfEmpty(Mono.error(consentArtefactNotFound())))
                .collectList();
    }
}
