package pl.edu.agh.dp.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.edu.agh.dp.api.annotations.Column;
import pl.edu.agh.dp.api.annotations.Entity;

/**
 * Powiadomienie push - dziedziczy z Notification (TABLE_PER_CLASS).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
public class PushNotification extends Notification {

    @Column(nullable = false)
    private String deviceToken;

    private String platform;

    private String category;

    private String actionUrl;

    private String imageUrl;

    private Integer badgeCount;

    @Column(defaultValue = "false")
    private Boolean isSilent;

    private Integer timeToLive;
}
