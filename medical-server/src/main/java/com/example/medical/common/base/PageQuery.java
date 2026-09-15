package com.example.medical.common.base;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * Request binding for the endpoints that take a page as a query object. The
 * bounds live in {@link Pages}, which is also what turns a page into a
 * {@code Pageable}, so the limit cannot drift between the two.
 */
@Data
public class PageQuery {

    @Min(value = 1, message = "Page must be at least 1")
    private long page = 1;

    @Min(value = 1, message = "Size must be at least 1")
    @Max(value = Pages.MAX_SIZE, message = "Size must be at most " + Pages.MAX_SIZE)
    private long size = Pages.DEFAULT_SIZE;
}
