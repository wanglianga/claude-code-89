package com.clothing.recycle.repo;

import com.clothing.recycle.model.AidDistribution;
import com.clothing.recycle.model.AidOrderAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AidAllocationRepo extends JpaRepository<AidOrderAllocation, Long> {
    /** 按 id 排序，保证多来源单签收/释放时的扣减顺序稳定 */
    List<AidOrderAllocation> findByDistributionOrderByIdAsc(AidDistribution distribution);

    /** 某批次下全部任务从来源单真实签收的件数（公示与批次发放件数唯一口径） */
    @Query("select coalesce(sum(a.issuedQuantity), 0) from AidOrderAllocation a "
            + "where a.distribution.batch.id = :batchId")
    int sumIssuedByBatchId(@Param("batchId") Long batchId);
}
