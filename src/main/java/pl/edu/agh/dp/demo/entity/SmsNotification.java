package pl.edu.agh.dp.demo.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.edu.agh.dp.api.annotations.Column;

/**
 * Powiadomienie SMS - dziedziczy z Notification (TABLE_PER_CLASS).
 */
@Getter
@Setter
@NoArgsConstructor
public class SmsNotification extends Notification {

    @Column(nullable = false)
    private String phoneNumber;

    private String senderNumber;

    private String carrier;

    private Boolean deliveryReport;

    private Integer messageParts;
}
