package com.healthdekho.insurance.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.healthdekho.insurance.dto.policy.ProductResponse;
import com.healthdekho.insurance.security.AuthenticatedUser;
import com.healthdekho.insurance.service.ProductService;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/available")
    public List<ProductResponse> available(AuthenticatedUser user) {
        return productService.availableProducts(user);
    }

    @GetMapping("/{productCode}")
    public ProductResponse product(@PathVariable String productCode) {
        return productService.product(productCode);
    }
}
