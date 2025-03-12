package in.org.projecteka.hiu.consent.model.consentmanager;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.org.projecteka.hiu.common.Constants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@AllArgsConstructor
@Builder
@NoArgsConstructor
@Data
public class Purpose {
    private String text;
    private String code;
    private final String refUri = Constants.CONSENT_PURPOSE_REF_URI;
}
