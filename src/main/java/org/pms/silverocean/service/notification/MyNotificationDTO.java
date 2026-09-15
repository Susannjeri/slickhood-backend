package org.pms.silverocean.service.notification;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

public record MyNotificationDTO(long id, String channel, String notificationType, String message,
                                boolean delivered, boolean read, ZonedDateTime createdOn, LocalDateTime lastUpdatedOn,
                                String actionPath,String actionStatus) {
    public MyNotificationDTO(long id,String channel,String type,String message,boolean delivered,boolean read,ZonedDateTime createdOn,LocalDateTime updatedOn){
        this(id,channel,type,message,delivered,read,createdOn,updatedOn,null,null);
    }
}
