package com.example.medical.common.base;

import lombok.Data;

@Data
public class PageQuery {

    private long page = 1;
    private long size = 10;
}
