package in.org.projecteka.hiu.common;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static in.org.projecteka.hiu.common.Constants.TIMESTAMP_PATTERN;
import static java.time.LocalDateTime.now;
import static java.time.ZoneOffset.UTC;

public class Utils {

    public static String getISOTimestamp(){
        return now(UTC).format(DateTimeFormatter.ofPattern(TIMESTAMP_PATTERN));
    }

    public static LocalDateTime parseTimeStamp(String timestamp){
        return LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern(TIMESTAMP_PATTERN));
    }
}
