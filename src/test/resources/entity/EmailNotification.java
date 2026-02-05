package pl.edu.agh.dp.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.edu.agh.dp.api.annotations.Column;
import pl.edu.agh.dp.api.annotations.Entity;

/**
 * Powiadomienie email - dziedziczy z Notification (TABLE_PER_CLASS).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
public class EmailNotification extends Notification {

    @Column(nullable = false)
    private String recipientEmail;

    private String subject;

    private String senderEmail;

    private String ccEmails;

    private String bccEmails;

    @Column(defaultValue = "false")
    private Boolean isHtml;

    private String attachments;
}
