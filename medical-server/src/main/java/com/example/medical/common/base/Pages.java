package com.example.medical.common.base;

import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * The single place a user-facing page becomes a {@link Pageable}.
 * <p>
 * Page and size arrive as raw {@code @RequestParam}s on most endpoints and
 * through {@link PageQuery} on the rest, so bounds have to be enforced wherever
 * a pageable is built — previously nothing enforced them at all and
 * {@code ?size=9999} was served. Out-of-range values are rejected rather than
 * clamped: a client asking for 10 000 rows should be told no, not silently given
 * 200.
 * <p>
 * Internal scans (the CSV export paging loop, the FHIR {@code _count} cap) build
 * their own pageables: they are not user page requests.
 */
public final class Pages {

    /** Largest page any user-facing endpoint will serve. */
    public static final int MAX_SIZE = 200;

    public static final int DEFAULT_SIZE = 10;

    private Pages() {
    }

    public static Pageable of(long page, long size) {
        return of(page, size, Sort.unsorted());
    }

    public static Pageable of(long page, long size, Sort sort) {
        if (page < 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Page must be at least 1");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Size must be between 1 and " + MAX_SIZE);
        }
        return PageRequest.of((int) (page - 1), (int) size, sort);
    }
}
