package com.wearhouse.product.support.web;

import com.wearhouse.product.domain.model.BuyerProductSortType;
import com.wearhouse.product.domain.model.Category;
import java.util.Locale;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ProductWebConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, Category.class, source -> {
            if (source == null || source.isBlank()) {
                return null;
            }
            return Category.valueOf(source.trim().toUpperCase(Locale.ROOT));
        });

        registry.addConverter(String.class, BuyerProductSortType.class, source -> {
            if (source == null || source.isBlank()) {
                return BuyerProductSortType.LATEST;
            }
            String normalized = source.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            return BuyerProductSortType.valueOf(normalized);
        });
    }
}
