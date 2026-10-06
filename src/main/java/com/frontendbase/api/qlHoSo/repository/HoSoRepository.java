package com.frontendbase.api.qlHoSo.repository;

import com.frontendbase.api.qlHoSo.entity.HoSo;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface HoSoRepository extends JpaRepository<HoSo, UUID>, JpaSpecificationExecutor<HoSo> {

}
