package pl.edu.agh.dp.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.edu.agh.dp.api.annotations.Column;
import pl.edu.agh.dp.api.annotations.Entity;

/**
 * Powiadomienie SMS - dziedziczy z Notification (TABLE_PER_CLASS).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
public class SmsNotification extends Notification {

    @Column(nullable = false)
    private String phoneNumber;

    private String senderNumber;

    private String carrier;

    private Boolean deliveryReport;

    private Integer messageParts;
}
