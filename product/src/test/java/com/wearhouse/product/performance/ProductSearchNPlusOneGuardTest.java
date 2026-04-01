package com.wearhouse.product.performance;

import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.common.support.querydsl.QuerydslConfig;
import com.wearhouse.common.support.s3.S3StorageService;
import com.wearhouse.product.buyer.mapper.BuyerProductResponseMapper;
import com.wearhouse.product.domain.entity.ProductEntity;
import com.wearhouse.product.domain.model.BuyerProductSortType;
import com.wearhouse.product.domain.model.Category;
import com.wearhouse.product.domain.model.ProductImageType;
import com.wearhouse.product.domain.model.ProductStatus;
import com.wearhouse.product.domain.repository.ProductImageRepository;
import com.wearhouse.product.domain.repository.ProductOptionRepository;
import com.wearhouse.product.domain.repository.ProductRepository;
import com.wearhouse.product.domain.repository.ProductSeasonRepository;
import com.wearhouse.product.domain.service.buyer.BuyerProductQueryService;
import com.wearhouse.product.domain.service.common.ProductImageUrlResolver;
import com.wearhouse.product.domain.service.common.ProductStockResolver;
import com.wearhouse.product.domain.service.seller.SellerProductAccessValidator;
import com.wearhouse.product.domain.service.seller.SellerProductQueryService;
import com.wearhouse.product.infra.inventory.ProductInventoryClient;
import com.wearhouse.product.seller.mapper.SellerProductResponseMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.format_sql=false",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=false"
})
@Import({
        QuerydslConfig.class,
        BuyerProductQueryService.class,
        SellerProductQueryService.class,
        SellerProductAccessValidator.class,
        ProductStockResolver.class,
        ProductImageUrlResolver.class,
        BuyerProductResponseMapper.class,
        SellerProductResponseMapper.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProductSearchNPlusOneGuardTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductOptionRepository productOptionRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private ProductSeasonRepository productSeasonRepository;

    @Autowired
    private BuyerProductQueryService buyerProductQueryService;

    @Autowired
    private SellerProductQueryService sellerProductQueryService;

    @Autowired
    private jakarta.persistence.EntityManagerFactory entityManagerFactory;

    @MockBean
    private ProductInventoryClient productInventoryClient;

    @MockBean
    private S3StorageService s3StorageService;

    @BeforeEach
    void setUp() {
        productImageRepository.deleteAll();
        productOptionRepository.deleteAll();
        productRepository.deleteAll();
        productSeasonRepository.deleteAll();

        for (int i = 1; i <= 30; i++) {
            ProductEntity product = ProductEntity.of(
                    700001L,
                    "nplusone-guard-" + i,
                    BigDecimal.valueOf(10_000L + i),
                    Category.TOP,
                    "details-" + i,
                    "size-guide",
                    "shipping",
                    ProductStatus.RELEASED
            );
            product.addOption("M", "BLACK", 100, 0);
            product.addOption("L", "NAVY", 80, 1);
            product.addImage(ProductImageType.MAIN, "products/main/" + i + ".jpg", 0);
            product.addImage(ProductImageType.PREVIEW, "products/preview/" + i + ".jpg", 0);
            productRepository.save(product);
        }

        given(productInventoryClient.getAvailableQtyBulk(any())).willReturn(Map.of());
        given(s3StorageService.getImageUrl(any())).willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void buyerListShouldNotTriggerNPlusOneQueries() {
        Statistics statistics = statistics();
        statistics.clear();

        buyerProductQueryService.getBuyerProducts(Category.TOP, BuyerProductSortType.LATEST, null, 20);

        long queryExecutions = statistics.getQueryExecutionCount();
        assertTrue(
                queryExecutions <= 3,
                "buyer list query count too high: " + queryExecutions
        );
    }

    @Test
    void sellerListShouldNotTriggerNPlusOneQueries() {
        Statistics statistics = statistics();
        statistics.clear();

        LoginUser seller = new LoginUser(700001L, "SELLER", List.of("ROLE_SELLER"), 1L);
        sellerProductQueryService.getSellerProducts(seller, null, null, null, 20, null);

        long queryExecutions = statistics.getQueryExecutionCount();
        assertTrue(
                queryExecutions <= 4,
                "seller list query count too high: " + queryExecutions
        );
    }

    private Statistics statistics() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
        return statistics;
    }
}
