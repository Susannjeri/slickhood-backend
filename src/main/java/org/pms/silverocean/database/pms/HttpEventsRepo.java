package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.HttpEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HttpEventsRepo extends JpaRepository<HttpEvent, Long> {
}
