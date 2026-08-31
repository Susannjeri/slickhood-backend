package org.pms.silverocean.database.pms;
import org.pms.silverocean.database.pms.entities.EstateResolution;
import org.springframework.data.domain.Page;import org.springframework.data.domain.Pageable;import org.springframework.data.jpa.repository.JpaRepository;
public interface EstateResolutionRepo extends JpaRepository<EstateResolution,Long>{Page<EstateResolution>findAllByMeetingIdAndActiveTrue(long meetingId,Pageable pageable);}
