package in.org.projecteka.hiu.patient;

import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.GatewayProperties;
import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.clients.AbhaAddressServiceClient;
import in.org.projecteka.hiu.clients.GatewayServiceClient;
import in.org.projecteka.hiu.clients.Patient;
import in.org.projecteka.hiu.common.Gateway;
import in.org.projecteka.hiu.common.cache.CacheAdapter;
import in.org.projecteka.hiu.consent.PatientConsentService;
import in.org.projecteka.hiu.patient.model.FindPatientRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.TimeoutException;

import static in.org.projecteka.hiu.common.TestBuilders.*;
import static in.org.projecteka.hiu.consent.TestBuilders.patient;
import static in.org.projecteka.hiu.consent.TestBuilders.randomString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static reactor.core.publisher.Mono.empty;
import static reactor.core.publisher.Mono.just;

class PatientServiceTest {

    @Mock
    CacheAdapter<String, Patient> cache;

    @Mock
    Gateway gateway;

    @Mock
    HiuProperties hiuProperties;

    @Mock
    GatewayProperties gatewayProperties;

    @Mock
    GatewayServiceClient gatewayServiceClient;

    @Mock
    PatientConsentService patientConsentService;

    @Mock
    AbhaAddressServiceClient abhaAddressServiceClient;

    @BeforeEach
    void init() {
        initMocks(this);
    }

    @Test
    void returnPatientFromCacheForFindPatient() {
        var patientId = randomString();
        var token = randomString();
        var patient = patient().build();
        when(cache.get(patientId)).thenReturn(just(patient));
        when(gateway.token()).thenReturn(just(token));
        var patientService = new PatientService(gatewayServiceClient,
                cache,
                hiuProperties,
                gatewayProperties,
                patientConsentService,
                abhaAddressServiceClient);

        Mono<Patient> patientPublisher = patientService.findPatientWith(patientId);

        StepVerifier.create(patientPublisher)
                .expectNext(patient)
                .verifyComplete();
        verify(abhaAddressServiceClient, never()).findPatientWith(any(), any());
    }

    @Test
    void shouldReturnGatewayTimeoutWhenNoResponseFromGatewayWithinTimeLimit() {
        var patientId = "temp@ncg";
        var token = randomString();
        when(hiuProperties.getId()).thenReturn(string());
        when(cache.get(patientId)).thenReturn(empty());
        when(gateway.token()).thenReturn(just(token));
        when(abhaAddressServiceClient.findPatientWith(any(), any())).thenReturn(Mono.error(new TimeoutException()));
        var patientService = new PatientService(gatewayServiceClient,
                cache,
                hiuProperties,
                gatewayProperties,
                patientConsentService,
                abhaAddressServiceClient);

        StepVerifier.create(patientService.findPatientWith(patientId))
                .expectErrorMatches(error -> ((ClientError) error)
                        .getError()
                        .getError()
                        .getMessage()
                        .equals("Could not connect to Gateway"))
                .verify();
    }

    @Test
    void shouldHandlePatientSearchResponse() {
        var id = "temp@ncg";
        var fullName = string();
        var searchResponse = abhaAddressSearchResponse().abhaAddress(id).fullName(fullName).build();
        when(cache.put(any(),any())).thenReturn(Mono.empty());
        when(cache.get(id)).thenReturn(empty());
        when(abhaAddressServiceClient.findPatientWith(eq(new FindPatientRequest(id)), any())).thenReturn(just(searchResponse));
        var patientService = new PatientService(gatewayServiceClient,
                cache,
                hiuProperties,
                gatewayProperties,
                patientConsentService,
                abhaAddressServiceClient);

        Mono<Patient> publisher = patientService.findPatientWith(id);

        StepVerifier.create(publisher).expectNext(new Patient(id, fullName, "")).verifyComplete();
    }
}
