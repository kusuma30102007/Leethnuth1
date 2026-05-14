package Leethnuth.syso.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import Leethnuth.syso.Order;



public interface Repo extends JpaRepository<Order, Long> {

    // Total orders with confirmed feedback for this area
    @Query("SELECT COUNT(o) FROM Order o WHERE o.area = :area AND o.deliveryStatus IS NOT NULL")
    long countConfirmedByArea(@Param("area") String area);

    // Only FAILED ones (user confirmed not delivered)
    @Query("SELECT COUNT(o) FROM Order o WHERE o.area = :area AND o.deliveryStatus = 'FAILED'")
    long countFailedByArea(@Param("area") String area);
}

