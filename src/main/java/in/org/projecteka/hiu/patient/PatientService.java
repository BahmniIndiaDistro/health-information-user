package in.org.projecteka.hiu.patient;

import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.GatewayProperties;
import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.clients.AbhaAddressServiceClient;
import in.org.projecteka.hiu.clients.GatewayServiceClient;
import in.org.projecteka.hiu.clients.Patient;
import in.org.projecteka.hiu.clients.PatientSearchThrowable;
import in.org.projecteka.hiu.common.DelayTimeoutException;
import in.org.projecteka.hiu.common.GatewayResponse;
import in.org.projecteka.hiu.common.cache.CacheAdapter;
import in.org.projecteka.hiu.consent.PatientConsentService;
import in.org.projecteka.hiu.patient.model.*;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static in.org.projecteka.hiu.ClientError.gatewayTimeOut;
import static in.org.projecteka.hiu.ErrorCode.PATIENT_NOT_FOUND;
import static in.org.projecteka.hiu.common.Constants.getCmSuffix;
import static in.org.projecteka.hiu.common.CustomScheduler.scheduleThis;
import static in.org.projecteka.hiu.consent.model.consentmanager.ConsentAcknowledgementStatus.OK;
import static java.time.Duration.ofMillis;
import static org.slf4j.LoggerFactory.getLogger;
import static reactor.core.publisher.Mono.defer;
import static reactor.core.publisher.Mono.empty;
import static reactor.core.publisher.Mono.error;

@AllArgsConstructor
public class PatientService {
    private static final Logger logger = getLogger(PatientService.class);
    private final GatewayServiceClient gatewayServiceClient;
    private final CacheAdapter<String, Patient> cache;
    private final HiuProperties hiuProperties;
    private final GatewayProperties gatewayProperties;
    private final PatientConsentService patientConsentService;

    private final AbhaAddressServiceClient abhaAddressServiceClient;

    private Mono<Patient> apply(AbhaAddressSearchResponse response) {
        Patient patient = response.toPatient();
        return cache.put(patient.getIdentifier(),patient).thenReturn(patient);
    }

    public Mono<Patient> tryFind(String id) {
        return findPatientWith(id)
                .onErrorResume(error -> error instanceof ClientError &&
                                ((ClientError) error).getError().getError().getCode() == PATIENT_NOT_FOUND,
                        error -> {
                            logger.error("Consent request created for unknown user.");
                            logger.error(error.getMessage(), error);
                            return empty();
                        });
    }

    public Mono<Patient> findPatientWith(String id) {
        return getFromCache(id, () ->
        {
            logger.info("about to get patient details from CM for: {}", id);
            var cmSuffix = getCmSuffix(id);
            var request = getFindPatientRequest(id);
            return scheduleThis(abhaAddressServiceClient.findPatientWith(request, cmSuffix))
                    .timeout(ofMillis(gatewayProperties.getRequestTimeout()))
                    .responseFrom(this::apply)
                    .onErrorResume(DelayTimeoutException.class, discard -> error(gatewayTimeOut()))
                    .onErrorResume(TimeoutException.class, discard -> error(gatewayTimeOut()))
                    .onErrorResume(PatientSearchThrowable.class, discard -> error(discard));
        });
    }

    private FindPatientRequest getFindPatientRequest(String id) {
        return new FindPatientRequest(id);
    }

    private Mono<Patient> getFromCache(String key, Supplier<Mono<Patient>> function) {
        return cache.get(key).switchIfEmpty(defer(function));
    }

    public Mono<Void> perform(HiuPatientStatusNotification hiuPatientStatusNotification) {
        final String healthId = hiuPatientStatusNotification.notification.patient.id;
        final String status = hiuPatientStatusNotification.notification.status.toString();
        if (status.equals(Status.DELETED.toString())) {
            return patientConsentService.deleteHealthId(healthId)
                .then(gatewayServiceClient.sendPatientStatusOnNotify(healthId.split("@")[1], buildPatientStatusOnNotify(hiuPatientStatusNotification.requestId)));
        }
        return null;
    }


    private PatientStatusNotification buildPatientStatusOnNotify(UUID requestID) {
        var requestId = UUID.randomUUID();
        var patientOnNotifyRequest = PatientStatusNotification
                .builder()
                .timestamp(LocalDateTime.now(ZoneOffset.UTC))
                .requestId(requestId);
        GatewayResponse gatewayResponse = new GatewayResponse(requestID.toString());
        patientOnNotifyRequest.resp(gatewayResponse).build();
        return patientOnNotifyRequest.acknowledgement(PatientStatusAcknowledgment.builder().status(OK).build()).build();
    }
}
