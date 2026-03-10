package com.wearhouse.inventory.domain.controller;

import static com.wearhouse.inventory.support.restdocs.ApiDocumentUtils.getDocumentRequest;
import static com.wearhouse.inventory.support.restdocs.ApiDocumentUtils.getDocumentResponse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.common.global.error.GlobalExceptionHandler;
import com.wearhouse.common.global.response.GlobalResponseBodyAdvice;
import com.wearhouse.inventory.domain.dto.request.InventoryAvailabilityCheckRequest;
import com.wearhouse.inventory.domain.dto.request.InventoryAvailabilityCheckRequest.InventoryAvailabilityLineRequest;
import com.wearhouse.inventory.domain.dto.request.InventoryStockUpsertRequest;
import com.wearhouse.inventory.domain.dto.response.InventoryAvailabilityCheckResponse;
import com.wearhouse.inventory.domain.dto.response.InventoryAvailabilityCheckResponse.InventoryAvailabilityLineResponse;
import com.wearhouse.inventory.domain.dto.response.InventoryStockResponse;
import java.math.BigDecimal;
import com.wearhouse.inventory.domain.service.command.InventoryStockService;
import com.wearhouse.inventory.domain.service.query.InventoryAvailabilityService;
import com.wearhouse.inventory.domain.service.query.InventoryStockReadService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InventoryController.class)
@AutoConfigureMockMvc(addFilters = false)
@AutoConfigureRestDocs
@Import({GlobalResponseBodyAdvice.class, GlobalExceptionHandler.class})
class InventoryControllerDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private InventoryStockService inventoryStockService;

    @MockitoBean
    private InventoryStockReadService inventoryStockReadService;

    @MockitoBean
    private InventoryAvailabilityService inventoryAvailabilityService;

    @Test
    @DisplayName("재고 upsert API 문서화")
    void upsertStock() throws Exception {
        InventoryStockUpsertRequest request = new InventoryStockUpsertRequest(
                1001L,
                50,
                11L,
                501L,
                "Debug Product",
                new BigDecimal("50000"),
                "OUTER",
                "S",
                "Black",
                "https://cdn.example.com/main.jpg"
        );
        InventoryStockResponse response = new InventoryStockResponse(
                1001L,
                11L,
                501L,
                "Debug Product",
                new BigDecimal("50000"),
                "OUTER",
                "S",
                "Black",
                "https://cdn.example.com/main.jpg",
                50,
                0,
                1L
        );
        given(inventoryStockService.upsert(any(InventoryStockUpsertRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/v1/internal/inventory/stocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andDo(document("inventory-stock-upsert",
                        getDocumentRequest(),
                        getDocumentResponse(),
                        requestFields(
                                fieldWithPath("skuId").type(JsonFieldType.NUMBER).description("SKU ID"),
                                fieldWithPath("availableQty").type(JsonFieldType.NUMBER).description("가용 재고 수량"),
                                fieldWithPath("sellerId").type(JsonFieldType.NUMBER).description("판매자 ID"),
                                fieldWithPath("productId").type(JsonFieldType.NUMBER).description("상품 ID"),
                                fieldWithPath("productName").type(JsonFieldType.STRING).description("상품명"),
                                fieldWithPath("productPrice").type(JsonFieldType.NUMBER).description("상품 가격"),
                                fieldWithPath("category").type(JsonFieldType.STRING).description("카테고리"),
                                fieldWithPath("size").type(JsonFieldType.STRING).description("옵션 사이즈"),
                                fieldWithPath("color").type(JsonFieldType.STRING).description("옵션 색상"),
                                fieldWithPath("mainImageUrl").type(JsonFieldType.STRING).description("대표 이미지 URL")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("data.skuId").type(JsonFieldType.NUMBER).description("SKU ID"),
                                fieldWithPath("data.sellerId").type(JsonFieldType.NUMBER).description("판매자 ID"),
                                fieldWithPath("data.productId").type(JsonFieldType.NUMBER).description("상품 ID"),
                                fieldWithPath("data.productName").type(JsonFieldType.STRING).description("상품명"),
                                fieldWithPath("data.productPrice").type(JsonFieldType.NUMBER).description("상품 가격"),
                                fieldWithPath("data.category").type(JsonFieldType.STRING).description("카테고리"),
                                fieldWithPath("data.size").type(JsonFieldType.STRING).description("옵션 사이즈"),
                                fieldWithPath("data.color").type(JsonFieldType.STRING).description("옵션 색상"),
                                fieldWithPath("data.mainImageUrl").type(JsonFieldType.STRING).description("대표 이미지 URL"),
                                fieldWithPath("data.availableQty").type(JsonFieldType.NUMBER).description("가용 재고 수량"),
                                fieldWithPath("data.reservedQty").type(JsonFieldType.NUMBER).description("예약 재고 수량"),
                                fieldWithPath("data.version").type(JsonFieldType.NUMBER).description("낙관락 버전"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시각")
                        )
                ));
    }

    @Test
    @DisplayName("재고 조회 API 문서화")
    void getStock() throws Exception {
        InventoryStockResponse response = new InventoryStockResponse(
                1001L,
                11L,
                501L,
                "Debug Product",
                new BigDecimal("50000"),
                "OUTER",
                "S",
                "Black",
                "https://cdn.example.com/main.jpg",
                50,
                3,
                7L
        );
        given(inventoryStockReadService.getBySkuId(eq(1001L))).willReturn(response);

        mockMvc.perform(get("/api/v1/internal/inventory/stocks/{skuId}", 1001L))
                .andExpect(status().isOk())
                .andDo(document("inventory-stock-get",
                        getDocumentRequest(),
                        getDocumentResponse(),
                        pathParameters(
                                parameterWithName("skuId").description("조회할 SKU ID")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("data.skuId").type(JsonFieldType.NUMBER).description("SKU ID"),
                                fieldWithPath("data.sellerId").type(JsonFieldType.NUMBER).description("판매자 ID"),
                                fieldWithPath("data.productId").type(JsonFieldType.NUMBER).description("상품 ID"),
                                fieldWithPath("data.productName").type(JsonFieldType.STRING).description("상품명"),
                                fieldWithPath("data.productPrice").type(JsonFieldType.NUMBER).description("상품 가격"),
                                fieldWithPath("data.category").type(JsonFieldType.STRING).description("카테고리"),
                                fieldWithPath("data.size").type(JsonFieldType.STRING).description("옵션 사이즈"),
                                fieldWithPath("data.color").type(JsonFieldType.STRING).description("옵션 색상"),
                                fieldWithPath("data.mainImageUrl").type(JsonFieldType.STRING).description("대표 이미지 URL"),
                                fieldWithPath("data.availableQty").type(JsonFieldType.NUMBER).description("가용 재고 수량"),
                                fieldWithPath("data.reservedQty").type(JsonFieldType.NUMBER).description("예약 재고 수량"),
                                fieldWithPath("data.version").type(JsonFieldType.NUMBER).description("낙관락 버전"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시각")
                        )
                ));
    }

    @Test
    @DisplayName("재고 가용성 체크 API 문서화")
    void checkAvailability() throws Exception {
        InventoryAvailabilityCheckRequest request = new InventoryAvailabilityCheckRequest(List.of(
                new InventoryAvailabilityLineRequest(5001L, 7001L, 2),
                new InventoryAvailabilityLineRequest(5002L, 7002L, 1)
        ));

        InventoryAvailabilityCheckResponse response = new InventoryAvailabilityCheckResponse(
                true,
                List.of(
                        new InventoryAvailabilityLineResponse(5001L, 7001L, 7001L, 2, 9, true),
                        new InventoryAvailabilityLineResponse(5002L, 7002L, 7002L, 1, 3, true)
                )
        );
        given(inventoryAvailabilityService.check(any(InventoryAvailabilityCheckRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/v1/internal/inventory/stocks/availability/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andDo(document("inventory-availability-check",
                        getDocumentRequest(),
                        getDocumentResponse(),
                        requestFields(
                                fieldWithPath("items[].productId").type(JsonFieldType.NUMBER).description("상품 ID"),
                                fieldWithPath("items[].optionId").type(JsonFieldType.NUMBER).optional().description("옵션 ID"),
                                fieldWithPath("items[].quantity").type(JsonFieldType.NUMBER).description("요청 수량")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("data.available").type(JsonFieldType.BOOLEAN).description("요청 전체 가용 여부"),
                                fieldWithPath("data.items[].productId").type(JsonFieldType.NUMBER).description("상품 ID"),
                                fieldWithPath("data.items[].optionId").type(JsonFieldType.NUMBER).description("옵션 ID"),
                                fieldWithPath("data.items[].skuId").type(JsonFieldType.NUMBER).description("계산된 SKU ID"),
                                fieldWithPath("data.items[].requestedQty").type(JsonFieldType.NUMBER).description("요청 수량"),
                                fieldWithPath("data.items[].availableQty").type(JsonFieldType.NUMBER).description("현재 가용 수량"),
                                fieldWithPath("data.items[].available").type(JsonFieldType.BOOLEAN).description("라인 가용 여부"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시각")
                        )
                ));
    }
}
