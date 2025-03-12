package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.common.GatewayResponse;
import in.org.projecteka.hiu.consent.model.ConsentArtefact;
import in.org.projecteka.hiu.consent.model.ConsentArtefactReference;
import in.org.projecteka.hiu.consent.model.ConsentNotification;
import in.org.projecteka.hiu.consent.model.ConsentStatus;
import in.org.projecteka.hiu.consent.model.consentmanager.ConsentAcknowledgement;
import in.org.projecteka.hiu.consent.model.consentmanager.ConsentOnNotifyRequest;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static in.org.projecteka.hiu.common.Constants.getCmSuffix;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.REQUESTED;
import static in.org.projecteka.hiu.consent.model.consentmanager.ConsentAcknowledgementStatus.OK;

@AllArgsConstructor
public abstract class ConsentTask {
    protected final ConsentRepository consentRepository;

    abstract Mono<Void> perform(ConsentNotification consentNotification, LocalDateTime timeStamp, UUID requestID);

    public Mono<Void> processNotificationRequest(String consentRequestId,
                                                 ConsentStatus status) {
        return consentRepository.getConsentRequestStatus(consentRequestId)
                .switchIfEmpty(Mono.error(ClientError.consentRequestNotFound()))
                .filter(consentStatus -> consentStatus == REQUESTED)
                .switchIfEmpty(Mono.error(ClientError.consentRequestAlreadyUpdated()))
                .flatMap(consentRequest -> consentRepository.updateConsentRequestStatus(status, consentRequestId));
    }

    public ConsentOnNotifyRequest buildConsentOnNotifyRequest(List<ConsentArtefact> consentArtefacts, UUID responseRequestId) {
        var consentArtefactRequest = ConsentOnNotifyRequest
                .builder();
        var acknowledgements = new ArrayList<ConsentAcknowledgement>();

        for (ConsentArtefact consentArtefact : consentArtefacts) {
            acknowledgements.add(ConsentAcknowledgement.builder().consentId(consentArtefact.getConsentId()).status(OK).build());
        }
        GatewayResponse gatewayResponse = new GatewayResponse(responseRequestId.toString());
        consentArtefactRequest.response(gatewayResponse).build();
        return consentArtefactRequest.acknowledgement(acknowledgements).build();
    }

    public ConsentOnNotifyRequest buildConsentOnNotifyRequestForReference(List<ConsentArtefactReference> consentArtefacts, UUID responseRequestId) {
        var consentArtefactRequest = ConsentOnNotifyRequest
                .builder();
        var acknowledgements = new ArrayList<ConsentAcknowledgement>();
        for (ConsentArtefactReference consentArtefact : consentArtefacts) {
            acknowledgements.add(ConsentAcknowledgement.builder().consentId(consentArtefact.getId()).status(OK).build());
        }
        GatewayResponse gatewayResponse = new GatewayResponse(responseRequestId.toString());
        consentArtefactRequest.response(gatewayResponse).build();
        return consentArtefactRequest.acknowledgement(acknowledgements).build();
    }

    public String getCmSuffixFromArtefact(List<ConsentArtefact> consentArtefacts) {
        if(consentArtefacts.isEmpty()){
            throw new RuntimeException("Consent artefacts are empty. Unable to get CM Suffix.");
        }
        ConsentArtefact consentArtefact = consentArtefacts.get(0);
        return getCmSuffix(consentArtefact.getPatient().getId());
    }
}
