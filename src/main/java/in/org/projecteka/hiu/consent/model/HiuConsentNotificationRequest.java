package in.org.projecteka.hiu.consent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
public class HiuConsentNotificationRequest {
    private ConsentNotification notification;


    public ConsentNotification getNotification() {
        return notification;
    }

    public void setNotification(ConsentNotification notification) {
        this.notification = notification;
    }
}
