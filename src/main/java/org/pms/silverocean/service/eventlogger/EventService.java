package org.pms.silverocean.service.eventlogger;

import org.pms.silverocean.database.pms.entities.HttpEvent;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EventService {
    private final Map<Long, Set<HttpEvent>> httpEventMap = new ConcurrentHashMap<>();
    private final EventDao eventDao;

    public EventService(EventDao eventDao) {
        this.eventDao = eventDao;
    }

    public <B> void cacheEvent(B request, Long id) {
        HttpEvent event = new HttpEvent();
        event.setEventType(request.getClass().getSimpleName());
        event.setEvent(request.toString().getBytes());
        event.setTId(id);

        cacheHttpEvent(event);
    }

    public <B> void saveEvent(B request, Long id) {
        HttpEvent event = new HttpEvent();
        event.setEventType(request.getClass().getSimpleName());
        event.setEvent(request.toString().getBytes());
        event.setTId(id);

        eventDao.saveEvent(event);
    }

    public <B> void saveEvent(B request, int statusCode, Long id) {
        HttpEvent event = new HttpEvent();
        event.setEventType(request.getClass().getSimpleName());
        event.setEvent(request.toString().getBytes());
        event.setHttpStatusCode(statusCode);
        event.setTId(id);

        eventDao.saveEvent(event);
    }

    public void saveEvent(HttpEvent httpEvent) {
        eventDao.saveEvent(httpEvent);
    }

    public void flushByTId(Long tId) {
        if (tId != null && httpEventMap.containsKey(tId)) {
            eventDao.saveEvents(httpEventMap.remove(tId));
        }
    }

    private void cacheHttpEvent(HttpEvent httpEvent) {
        httpEventMap.computeIfAbsent(httpEvent.getTId(), k -> new HashSet<>()).add(httpEvent);
    }
}
