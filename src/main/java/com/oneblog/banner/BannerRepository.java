package com.oneblog.banner;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BannerRepository extends JpaRepository<Banner, Long> {

    @Query("select b from Banner b order by b.sortOrder asc, b.id asc")
    List<Banner> findAllOrdered();

    @Query("select b from Banner b where b.visible = true order by b.sortOrder asc, b.id asc")
    List<Banner> findVisible(Pageable pageable);
}
