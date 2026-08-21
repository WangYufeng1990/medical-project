package com.example.medical.common.base;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class PageQuery {

    @Min(value = 1, message = "Page must be at least 1")
    private long page = 1;

    @Min(value = 1, message = "Size must be at least 1")
    @Max(value = 200, message = "Size cannot exceed 200")
    private long size = 10;
}
