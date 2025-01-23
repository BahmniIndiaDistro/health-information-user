package in.org.projecteka.hiu.common;

import java.time.format.DateTimeFormatter;

import static java.time.LocalDateTime.now;
import static java.time.ZoneOffset.UTC;

public class Utils {

    static final String TIMESTAMP_PATTERN="yyyy-MM-dd['T'HH[:mm][:ss][.SSS]'Z']";
    public static String getISOTimestamp(){
        return now(UTC).format(DateTimeFormatter.ofPattern(TIMESTAMP_PATTERN));
    }
}
