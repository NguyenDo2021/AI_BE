package com.frontendbase.api.qlHoSo.service;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.frontendbase.api.common.exception.ApiException;
import com.frontendbase.api.qlHoSo.dto.HoSoPageResponse;
import com.frontendbase.api.qlHoSo.entity.HoSo;
import com.frontendbase.api.qlHoSo.mapper.HoSoMapper;
import com.frontendbase.api.qlHoSo.repository.HoSoRepository;

import jakarta.transaction.TransactionScoped;

@Service
public class HoSoService {
    private static final int Max_PAGE_SIZE = 100;

    private final HoSoRepository hoSoRepository;
    private final HoSoMapper hoSoMapper;

    public HoSoService(HoSoRepository hoSoRepository, HoSoMapper hoSoMapper) {
        this.hoSoRepository = hoSoRepository;
        this.hoSoMapper = hoSoMapper;
    }

    @Transactional(readOnly = true)
    public HoSoPageResponse findHoSo(int page, int pageSize, Integer status, String keyword) {
        if (page < 1 || pageSize < 1 || pageSize > Max_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION",
                    "Số trang hoặc kích thước trang không hợp lệ");
        }
    }
}
