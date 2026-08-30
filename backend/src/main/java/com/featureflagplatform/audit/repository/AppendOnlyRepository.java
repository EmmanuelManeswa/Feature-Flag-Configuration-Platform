package com.featureflagplatform.audit.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

import java.util.Optional;

/**
 * A Spring Data repository surface that only ever grows a table: {@code save}
 * (insert new rows) and reads, nothing else. {@link org.springframework.data.jpa.repository.JpaRepository}
 * was deliberately <b>not</b> used here — it inherits a full {@code delete}/
 * {@code deleteById}/{@code deleteAll} surface from {@code CrudRepository}
 * whether or not any application code calls it, which is exactly the gap an
 * automated review caught: "no code currently calls delete" is not the same
 * guarantee as "no code can call delete". This interface makes the audit log's
 * append-only invariant a compile-time fact instead of a convention — there is
 * no delete method to accidentally call, now or in a future change.
 */
@NoRepositoryBean
public interface AppendOnlyRepository<T, ID> extends Repository<T, ID> {

    <S extends T> S save(S entity);

    Optional<T> findById(ID id);

    Page<T> findAll(Pageable pageable);

    long count();
}
