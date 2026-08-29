package org.pms.silverocean.service.eventlogger;

import org.pms.silverocean.database.pms.HttpEventsRepo;
import org.pms.silverocean.database.pms.entities.HttpEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class EventDao {
    private final HttpEventsRepo httpEventsRepo;


    public EventDao(HttpEventsRepo httpEventsRepo) {
        this.httpEventsRepo = httpEventsRepo;
    }

    public void saveEvent(HttpEvent httpEvent) {
        httpEventsRepo.save(httpEvent);
    }

    public void saveEvents(Set<HttpEvent> httpEvent) {
        httpEventsRepo.saveAll(httpEvent);
    }

    public Page<HttpEvent> getEvents(Pageable pageable) {
        return httpEventsRepo.findAll(pageable);
    }
}
