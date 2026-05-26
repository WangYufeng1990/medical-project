package com.example.medical.common.result;

import java.util.List;

public record PageResult<T>(long total, long size, long current, List<T> records) {

    public static <T> PageResult<T> of(long total, long size, long current, List<T> records) {
        return new PageResult<>(total, size, current, records);
    }
}
