package org.pms.silverocean.database.pms;
import org.pms.silverocean.database.pms.entities.EstateWorkOrder;
import org.springframework.data.domain.Page;import org.springframework.data.domain.Pageable;import org.springframework.data.jpa.repository.JpaRepository;
public interface EstateWorkOrderRepo extends JpaRepository<EstateWorkOrder,Long>{Page<EstateWorkOrder>findAllByPropertyIdAndActiveTrue(long propertyId,Pageable pageable);}
