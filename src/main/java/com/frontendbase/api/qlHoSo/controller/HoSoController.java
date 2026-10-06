package com.frontendbase.api.qlHoSo.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/ql-ho-so")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class HoSoController {

}
