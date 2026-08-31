package org.pms.silverocean.database.pms;
import org.pms.silverocean.database.pms.entities.EstateMeeting;
import org.springframework.data.domain.Page;import org.springframework.data.domain.Pageable;import org.springframework.data.jpa.repository.JpaRepository;
public interface EstateMeetingRepo extends JpaRepository<EstateMeeting,Long>{Page<EstateMeeting>findAllByPropertyIdAndActiveTrue(long propertyId,Pageable pageable);}
