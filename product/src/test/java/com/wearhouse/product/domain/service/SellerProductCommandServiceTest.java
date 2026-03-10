package com.wearhouse.product.domain.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.product.domain.dto.request.ProductCreateRequest;
import com.wearhouse.product.domain.dto.request.ProductOptionCreateRequest;
import com.wearhouse.product.domain.entity.ProductEntity;
import com.wearhouse.product.domain.entity.ProductOptionEntity;
import com.wearhouse.product.domain.model.Category;
import com.wearhouse.product.domain.model.ProductStatus;
import com.wearhouse.product.domain.repository.ProductRepository;
import com.wearhouse.product.domain.service.seller.SellerProductCommandService;
import com.wearhouse.product.infra.inventory.ProductInventoryClient;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SellerProductCommandServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductInventoryClient productInventoryClient;

    @Test
    void createProductShouldFlushBeforeInventorySync() {
        SellerProductCommandService service = new SellerProductCommandService(productRepository, productInventoryClient);
        LoginUser seller = new LoginUser(11L, "SELLER", List.of("ROLE_SELLER"), 1L);
        ProductCreateRequest request = new ProductCreateRequest(
                "Debug Product",
                new BigDecimal("50000"),
                Category.OUTER,
                "desc",
                "https://cdn.example.com/main.jpg",
                List.of("https://cdn.example.com/p1.jpg"),
                List.of("https://cdn.example.com/d1.jpg"),
                ProductStatus.PENDING,
                List.of(
                        new ProductOptionCreateRequest("S", "Black", 10, BigDecimal.ZERO),
                        new ProductOptionCreateRequest("M", "Black", 3, BigDecimal.ZERO)
                )
        );

        when(productRepository.saveAndFlush(any(ProductEntity.class))).thenAnswer(invocation -> {
            ProductEntity persisted = invocation.getArgument(0);
            ReflectionTestUtils.setField(persisted, "id", 501L);
            List<ProductOptionEntity> options = persisted.getOptions();
            ReflectionTestUtils.setField(options.get(0), "id", 1001L);
            ReflectionTestUtils.setField(options.get(1), "id", 1002L);
            return persisted;
        });

        service.createProduct(seller, request);

        verify(productRepository).saveAndFlush(any(ProductEntity.class));
        verify(productInventoryClient).upsertStock(
                eq(1001L),
                eq(10),
                eq(11L),
                eq(501L),
                eq("Debug Product"),
                eq(new BigDecimal("50000")),
                eq("OUTER"),
                eq("S"),
                eq("Black"),
                eq("https://cdn.example.com/main.jpg")
        );
        verify(productInventoryClient).upsertStock(
                eq(1002L),
                eq(3),
                eq(11L),
                eq(501L),
                eq("Debug Product"),
                eq(new BigDecimal("50000")),
                eq("OUTER"),
                eq("M"),
                eq("Black"),
                eq("https://cdn.example.com/main.jpg")
        );
    }
}
