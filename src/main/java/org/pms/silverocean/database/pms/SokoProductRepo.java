package org.pms.silverocean.database.pms;

import jakarta.persistence.LockModeType;
import org.pms.silverocean.database.pms.entities.SokoProduct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SokoProductRepo extends JpaRepository<SokoProduct, Long> {
    @Query(value="select p from SokoProduct p join SokoStore s on s.id=p.storeId where p.active=true and p.status='PUBLISHED' and p.stockQuantity>0 and s.active=true and s.status='PUBLISHED' and (:storeId is null or p.storeId=:storeId) and (:category is null or lower(p.category)=lower(:category)) and (:query is null or lower(p.name) like lower(concat('%',:query,'%')) or lower(p.description) like lower(concat('%',:query,'%')) or lower(s.name) like lower(concat('%',:query,'%'))) and (:minLat is null or (s.latitude between :minLat and :maxLat and s.longitude between :minLng and :maxLng)) order by case when :latitude is null then 0.0 else ((s.latitude-:latitude)*(s.latitude-:latitude)+(s.longitude-:longitude)*(s.longitude-:longitude)) end asc, p.name asc",
            countQuery="select count(p) from SokoProduct p join SokoStore s on s.id=p.storeId where p.active=true and p.status='PUBLISHED' and p.stockQuantity>0 and s.active=true and s.status='PUBLISHED' and (:storeId is null or p.storeId=:storeId) and (:category is null or lower(p.category)=lower(:category)) and (:query is null or lower(p.name) like lower(concat('%',:query,'%')) or lower(p.description) like lower(concat('%',:query,'%')) or lower(s.name) like lower(concat('%',:query,'%'))) and (:minLat is null or (s.latitude between :minLat and :maxLat and s.longitude between :minLng and :maxLng))")
    Page<SokoProduct> searchCatalog(Pageable pageable, Long storeId, String category, String query, Double latitude, Double longitude, Double minLat, Double maxLat, Double minLng, Double maxLng);

    List<SokoProduct> findAllByStoreIdAndActiveTrueOrderByName(long storeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SokoProduct p where p.id=:id and p.active=true")
    Optional<SokoProduct> findByIdForUpdate(long id);
    Page<SokoProduct> findAllByActiveTrue(Pageable pageable);
    long countByActiveTrue();
    long countByStatusAndActiveTrue(String status);
}
