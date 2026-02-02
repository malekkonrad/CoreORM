package pl.edu.agh.dp.demo.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.edu.agh.dp.api.annotations.Column;

/**
 * Powiadomienie email - dziedziczy z Notification (TABLE_PER_CLASS).
 */
@Getter
@Setter
@NoArgsConstructor
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
