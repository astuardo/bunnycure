package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.model.ServiceSupply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceSupplyRepository extends JpaRepository<ServiceSupply, Long> {

    @Override
    @Query("SELECT s FROM ServiceSupply s WHERE s.id = :id")
    java.util.Optional<ServiceSupply> findById(@Param("id") Long id);

    @Query("SELECT s FROM ServiceSupply s JOIN FETCH s.product WHERE s.service.id = :serviceId")
    List<ServiceSupply> findByServiceIdWithProduct(@Param("serviceId") Long serviceId);

    @Query("SELECT s FROM ServiceSupply s JOIN FETCH s.product WHERE s.service.id IN :serviceIds")
    List<ServiceSupply> findByServiceIdsWithProduct(@Param("serviceIds") List<Long> serviceIds);

    void deleteByServiceId(Long serviceId);
}
