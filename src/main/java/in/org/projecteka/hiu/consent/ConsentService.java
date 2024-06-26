package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.Error;
import in.org.projecteka.hiu.ErrorRepresentation;
import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.clients.GatewayServiceClient;
import in.org.projecteka.hiu.common.cache.CacheAdapter;
import in.org.projecteka.hiu.consent.model.ConsentNotification;
import in.org.projecteka.hiu.consent.model.ConsentRequestData;
import in.org.projecteka.hiu.consent.model.ConsentRequestInitResponse;
import in.org.projecteka.hiu.consent.model.ConsentRequestRepresentation;
import in.org.projecteka.hiu.consent.model.ConsentStatus;
import in.org.projecteka.hiu.consent.model.ConsentStatusRequest;
import in.org.projecteka.hiu.consent.model.GatewayConsentArtefactResponse;
import in.org.projecteka.hiu.consent.model.HiuConsentNotificationRequest;
import in.org.projecteka.hiu.consent.model.consentmanager.ConsentRequest;
import in.org.projecteka.hiu.patient.PatientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static in.org.projecteka.hiu.ClientError.consentRequestNotFound;
import static in.org.projecteka.hiu.ErrorCode.INVALID_PURPOSE_OF_USE;
import static in.org.projecteka.hiu.common.Constants.EMPTY_STRING;
import static in.org.projecteka.hiu.common.Constants.STATUS;
import static in.org.projecteka.hiu.common.Constants.getCmSuffix;
import static in.org.projecteka.hiu.consent.model.ConsentRequestRepresentation.toConsentRequestRepresentation;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.DENIED;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.ERRORED;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.EXPIRED;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.GRANTED;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.REVOKED;
import static java.time.LocalDateTime.now;
import static java.time.ZoneOffset.UTC;
import static java.util.UUID.fromString;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static reactor.core.publisher.Flux.fromIterable;
import static reactor.core.publisher.Mono.defer;
import static reactor.core.publisher.Mono.empty;
import static reactor.core.publisher.Mono.error;
import static reactor.core.publisher.Mono.just;

public class ConsentService {
    private static final Logger logger = LoggerFactory.getLogger(ConsentService.class);
    private final HiuProperties hiuProperties;
    private final ConsentRepository consentRepository;
    private final DataFlowRequestPublisher dataFlowRequestPublisher;
    private final DataFlowDeletePublisher dataFlowDeletePublisher;
    private final PatientService patientService;
    private final HealthInformationPublisher healthInformationPublisher;
    private final ConceptValidator conceptValidator;
    private final GatewayServiceClient gatewayServiceClient;
    private final CacheAdapter<String, String> responseCache;
    private final Map<ConsentStatus, ConsentTask> consentTasks;
    private final PatientConsentRepository patientConsentRepository;
    private final CacheAdapter<String, String> patientRequestCache;
    private final ConsentServiceProperties consentServiceProperties;

    public ConsentService(HiuProperties hiuProperties,
                          ConsentRepository consentRepository,
                          DataFlowRequestPublisher dataFlowRequestPublisher,
                          DataFlowDeletePublisher dataFlowDeletePublisher,
                          PatientService patientService,
                          HealthInformationPublisher healthInformationPublisher,
                          ConceptValidator conceptValidator,
                          GatewayServiceClient gatewayServiceClient,
                          PatientConsentRepository patientConsentRepository,
                          ConsentServiceProperties consentServiceProperties,
                          CacheAdapter<String, String> patientRequestCache,
                          CacheAdapter<String, String> responseCache) {
        this.hiuProperties = hiuProperties;
        this.consentRepository = consentRepository;
        this.dataFlowRequestPublisher = dataFlowRequestPublisher;
        this.dataFlowDeletePublisher = dataFlowDeletePublisher;
        this.patientService = patientService;
        this.healthInformationPublisher = healthInformationPublisher;
        this.conceptValidator = conceptValidator;
        this.gatewayServiceClient = gatewayServiceClient;
        this.patientConsentRepository = patientConsentRepository;
        this.consentServiceProperties = consentServiceProperties;
        consentTasks = new HashMap<>();
        this.patientRequestCache = patientRequestCache;
        this.responseCache = responseCache;
    }

    private Mono<Void> validateConsentRequest(ConsentRequestData consentRequestData) {
        return conceptValidator.validatePurpose(consentRequestData.getConsent().getPurpose().getCode())
                .filter(result -> result)
                .switchIfEmpty(Mono.error(new ClientError(INTERNAL_SERVER_ERROR,
                        new ErrorRepresentation(new Error(INVALID_PURPOSE_OF_USE,
                                "Invalid Purpose Of Use")))))
                .then();
    }

    public Mono<Void> createRequest(String requesterId, ConsentRequestData consentRequestData) {
        var gatewayRequestId = UUID.randomUUID();
        return validateConsentRequest(consentRequestData)
                .then(sendConsentRequestToGateway(requesterId, consentRequestData, gatewayRequestId));
    }

    private Mono<Void> sendConsentRequestToGateway(
            String requesterId,
            ConsentRequestData hiRequest,
            UUID gatewayRequestId) {
        var reqInfo = hiRequest.getConsent().to(requesterId, hiuProperties.getId(), conceptValidator);
        var patientId = hiRequest.getConsent().getPatient().getId();
        var consentRequest = ConsentRequest.builder()
                .requestId(gatewayRequestId)
                .timestamp(now(UTC))
                .consent(reqInfo)
                .build();
        var hiuConsentRequest = hiRequest.getConsent().toConsentRequest(gatewayRequestId.toString(), requesterId);
        return consentRepository.insertConsentRequestToGateway(hiuConsentRequest)
                .then(gatewayServiceClient.sendConsentRequest(getCmSuffix(patientId), consentRequest));
    }

    public Mono<Void> updatePostedRequest(ConsentRequestInitResponse response) {
        var requestId = response.getResp().getRequestId();
        if (response.getError() != null) {
            logger.error("[ConsentService] Received error response from consent-request. HIU " +
                            "RequestId={}, Error code = {}, message={}",
                    requestId,
                    response.getError().getCode(),
                    response.getError().getMessage());
            return consentRepository.updateConsentRequestStatus(requestId, ERRORED, EMPTY_STRING);
        }

        if (response.getConsentRequest() != null) {
            var updatePublisher = consentRepository.consentRequestStatus(requestId)
                    .switchIfEmpty(error(consentRequestNotFound()))
                    .flatMap(status -> updateConsentRequestStatus(response, status));
            return patientRequestCache.get(requestId)
                    .switchIfEmpty(error(new NoSuchFieldError()))
                    .map(UUID::fromString)
                    .flatMap(dataRequestId -> {
                        var consentRequestId = fromString(response.getConsentRequest().getId());
                        return updatePublisher
                                .then(patientConsentRepository.updatePatientConsentRequest(dataRequestId,
                                        consentRequestId,
                                        now(UTC)));
                    })
                    .onErrorResume(NoSuchFieldError.class, e -> updatePublisher);
        }

        return error(ClientError.invalidDataFromGateway());
    }

    private Mono<Void> updateConsentRequestStatus(ConsentRequestInitResponse consentRequestInitResponse,
                                                  ConsentStatus oldStatus) {
        if (oldStatus.equals(ConsentStatus.POSTED)) {
            return consentRepository.updateConsentRequestStatus(
                    consentRequestInitResponse.getResp().getRequestId(),
                    ConsentStatus.REQUESTED,
                    consentRequestInitResponse.getConsentRequest().getId());
        }
        return empty();
    }

    public Flux<ConsentRequestRepresentation> requestsOf(String requesterId) {
        return consentRepository.requestsOf(requesterId)
                .take(consentServiceProperties.getDefaultPageSize())
                .collectList()
                .flatMapMany(list -> {
                    // Warming up cache
                    Set<String> patients = new java.util.HashSet<>(Set.of());
                    for (var result : list) {
                        var consentRequest =
                                (in.org.projecteka.hiu.consent.model.ConsentRequest) result.get("consentRequest");
                        patients.add(consentRequest.getPatient().getId());
                    }
                    return fromIterable(patients).flatMap(patientService::tryFind).thenMany(fromIterable(list));
                })
                .flatMap(result -> {
                    var consentRequest =
                            (in.org.projecteka.hiu.consent.model.ConsentRequest) result.get("consentRequest");
                    var consentRequestId = (String) result.get("consentRequestId");
                    consentRequestId = consentRequestId == null ? EMPTY_STRING : consentRequestId;
                    var status = (ConsentStatus) result.get(STATUS);
                    return Mono.zip(patientService.tryFind(consentRequest.getPatient().getId()),
                            mergeWithArtefactStatus(consentRequest, status, consentRequestId),
                            just(consentRequestId));
                })
                .map(patientConsentRequest -> toConsentRequestRepresentation(patientConsentRequest.getT1(),
                        patientConsentRequest.getT2(),
                        patientConsentRequest.getT3()));
    }

    private Mono<in.org.projecteka.hiu.consent.model.ConsentRequest> mergeWithArtefactStatus(
            in.org.projecteka.hiu.consent.model.ConsentRequest consentRequest,
            ConsentStatus reqStatus,
            String consentRequestId) {
        var consent = consentRequest.toBuilder().status(reqStatus).build();
        return reqStatus.equals(ConsentStatus.POSTED)
                ? just(consent)
                : consentRepository.getConsentDetails(consentRequestId)
                .take(1)
                .next()
                .map(map -> ConsentStatus.valueOf(map.get(STATUS)))
                .switchIfEmpty(just(reqStatus))
                .map(artefactStatus -> consent.toBuilder().status(artefactStatus).build());
    }

    public Mono<Void> handleNotification(HiuConsentNotificationRequest hiuNotification) {
        return processConsentNotification(hiuNotification.getNotification(), hiuNotification.getTimestamp(), hiuNotification.getRequestId());
    }

    public Mono<Void> handleConsentArtefact(GatewayConsentArtefactResponse consentArtefactResponse) {
        if (consentArtefactResponse.getError() != null) {
            logger.error("[ConsentService] Received error response for consent-artefact. HIU " +
                            "RequestId={}, Error code = {}, message={}",
                    consentArtefactResponse.getResp().getRequestId(),
                    consentArtefactResponse.getError().getCode(),
                    consentArtefactResponse.getError().getMessage());
            return empty();
        }
        if (consentArtefactResponse.getConsent() != null) {
            return responseCache.get(consentArtefactResponse.getResp().getRequestId())
                    .flatMap(requestId -> consentRepository.insertConsentArtefact(
                            consentArtefactResponse.getConsent().getConsentDetail(),
                            consentArtefactResponse.getConsent().getStatus(),
                            requestId))
                    .then((defer(() -> dataFlowRequestPublisher.broadcastDataFlowRequest(
                            consentArtefactResponse.getConsent().getConsentDetail().getConsentId(),
                            consentArtefactResponse.getConsent().getConsentDetail().getPermission().getDateRange(),
                            consentArtefactResponse.getConsent().getSignature(),
                            hiuProperties.getDataPushUrl()))));
        }
        logger.error("Unusual response = {} from CM", consentArtefactResponse);
        return empty();
    }

    public Mono<Void> handleConsentRequestStatus(ConsentStatusRequest consentStatusRequest) {
        if (consentStatusRequest.getError() != null) {
            logger.error("[ConsentService] Received error response for consent-status. HIU " +
                            "RequestId={}, Error code = {}, message={}",
                    consentStatusRequest.getResp().getRequestId(),
                    consentStatusRequest.getError().getCode(),
                    consentStatusRequest.getError().getMessage());
            return empty();
        }
        if (consentStatusRequest.getConsentRequest() != null) {
            logger.info("[ConsentService] Received consent request response for consent-status: {}" +
                    consentStatusRequest.getConsentRequest());
            return consentRepository
                    .getConsentRequestStatus(consentStatusRequest.getConsentRequest().getId())
                    .switchIfEmpty(Mono.error(ClientError.consentRequestNotFound()))
                    .filter(consentStatus -> consentStatus != consentStatusRequest.getConsentRequest().getStatus())
                    .flatMap(consentRequest -> consentRepository
                            .updateConsentRequestStatus(consentStatusRequest.getConsentRequest().getStatus(),
                                    consentStatusRequest.getConsentRequest().getId()));
        }
        logger.error("Unusual response = {} from CM", consentStatusRequest);
        return empty();
    }

    private Mono<Void> processConsentNotification(ConsentNotification notification, LocalDateTime localDateTime, UUID requestId) {
        var consentTask = consentTasks.get(notification.getStatus());
        if (consentTask == null) {
            return error(ClientError.validationFailed());
        }
        return consentTask.perform(notification, localDateTime, requestId);
    }

    @PostConstruct
    private void postConstruct() {
        consentTasks.put(GRANTED, new GrantedConsentTask(consentRepository, gatewayServiceClient, responseCache));
        consentTasks.put(REVOKED, new RevokedConsentTask(consentRepository, healthInformationPublisher, gatewayServiceClient));
        consentTasks.put(EXPIRED, new ExpiredConsentTask(consentRepository, dataFlowDeletePublisher, gatewayServiceClient));
        consentTasks.put(DENIED, new DeniedConsentTask(consentRepository));
    }
}
