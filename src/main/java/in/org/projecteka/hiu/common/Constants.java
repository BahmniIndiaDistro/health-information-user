package in.org.projecteka.hiu.common;

public class Constants {
    // APIs
    private static final String CURRENT_VERSION = "/v0.5";
    public static final String PATH_CONSENT_REQUESTS_ON_INIT = "/api/v3/hiu/consent/request/on-init";
    public static final String PATH_CONSENTS_HIU_NOTIFY = "/api/v3/hiu/consent/request/notify";
    public static final String PATH_CONSENTS_ON_FETCH = "/api/v3/hiu/consent/on-fetch";
    public static final String PATH_HEALTH_INFORMATION_HIU_ON_REQUEST = "/api/v3/hiu/health-information/on-request";
    public static final String PATH_HEARTBEAT = CURRENT_VERSION + "/heartbeat";
    public static final String X_CM_ID = "X-CM-ID";
    public static final String PATH_DATA_TRANSFER = "/data/notification";
    public static final String EMPTY_STRING = "";
    public static final String APP_PATH_PATIENT_CONSENT_REQUEST = "/v1/patient/consent-request";
    public static final String APP_PATH_HIU_CONSENT_REQUESTS = "/v1/hiu/consent-requests";
    public static final String PATIENT_REQUESTED_PURPOSE_CODE = "PATRQT";
    public static final String API_PATH_FETCH_PATIENT_HEALTH_INFO = "/v1/patient/health-information/fetch";
    public static final String API_PATH_GET_INFO_FOR_SINGLE_CONSENT_REQUEST = "/health-information/fetch/{consent-request-id}";
    public static final String API_PATH_GET_ATTACHMENT = "/health-information/fetch/{consent-request-id}/attachments/{file-name}";
    public static final String CM_API_PATH_GET_ATTACHMENT = "/v1/patient/health-information/fetch/{consent-request-id}/attachments/{file-name}";
    public static final String API_PATH_GET_HEALTH_INFO_STATUS = "/v1/patient/health-information/status";
    public static final String INTERNAL_PATH_PATIENT_CARE_CONTEXT_INFO = "/internal/patient/hip/data-transfer-status";
    public static final String PATH_CONSENT_REQUEST_ON_STATUS = "/api/v3/hiu/consent/request/on-status";
    public static final String GET_CERT = "/certs";
    public static final String PATH_PATIENT_STATUS_NOTIFY = "/v0.5/patients/status/notify";
    public static final String PATH_PATIENT_STATUS_ON_NOTIFY = "/v0.5/patients/status/on-notify";

    public static final String STATUS = "status";
    public static final String DELIMITER = "@";
    public static final String BLOCK_LIST = "blockList";
    public static final String BLOCK_LIST_FORMAT = "%s:%s";
    public static final String CORRELATION_ID = "CORRELATION-ID";
    public static final String PATH_READINESS = CURRENT_VERSION + "/readiness";

    public static final String PATH_GATEWAY_SESSION = "/api/hiecm/gateway/v3/sessions";
    public static final String PATH_ABHA_ADDRESS_SEARCH = "/login/abha/search";

    public static final String GATEWAY_PATH_CONSENT_REQUESTS_INIT = "/api/hiecm/consent/v3/request/init";
    public static final String GATEWAY_PATH_CONSENT_ARTEFACT_FETCH =  "/api/hiecm/consent/v3/fetch";
    public static final String GATEWAY_PATH_CONSENT_ON_NOTIFY = "/api/hiecm/consent/v3/request/hiu/on-notify";
    public static final String GATEWAY_PATH_HEALTH_INFORMATION_REQUEST = "/api/hiecm/data-flow/v3/health-information/request";
    public static final String GATEWAY_PATH_HEALTH_INFORMATION_NOTIFY = "/api/hiecm/data-flow/v3/health-information/notify";
    public static final String REQUEST_ID = "REQUEST-ID";
    public static final String TIMESTAMP = "TIMESTAMP";
    public static final String X_HIU_ID = "X-HIU-ID";
    public static final String CONSENT_PURPOSE_REF_URI = "www.abdm.gov.in";
    public static final String REQUESTER_IDENTIFIER_TYPE = "HPIN";
    public static final String REQUESTER_IDENTIFIER_SYSTEM = "https://nrces.in/ndhm/fhir/r4/CodeSystem/ndhm-identifier-type-code";

    public static final String PATIENT_IDENTIFIER_TYPE = "ABHA";
    public static final String PATIENT_IDENTIFIER_SYSTEM = "https://nrces.in/ndhm/fhir/r4/CodeSystem/ndhm-identifier-type-code";

    public static final String TIMESTAMP_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    public static final String HPIN_NOT_FOUND_ERROR = "ABDM Health Professional Identifier value not found for user";

    private Constants() {
    }

    public static String getCmSuffix(String patientId) {
        return patientId.split(DELIMITER)[1];
    }
}
