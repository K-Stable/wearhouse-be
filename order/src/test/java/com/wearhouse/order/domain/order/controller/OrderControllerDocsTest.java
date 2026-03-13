package com.wearhouse.order.domain.controller;

import static com.wearhouse.order.support.restdocs.ApiDocumentUtils.getDocumentRequest;
import static com.wearhouse.order.support.restdocs.ApiDocumentUtils.getDocumentResponse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.common.global.error.GlobalExceptionHandler;
import com.wearhouse.common.global.response.GlobalResponseBodyAdvice;
import com.wearhouse.order.domain.dto.request.OrderCancelRequest;
import com.wearhouse.order.domain.dto.request.OrderCreateRequest;
import com.wearhouse.order.domain.dto.request.OrderCreateRequest.OrderCreateItemRequest;
import com.wearhouse.order.domain.dto.response.OrderCancelResponse;
import com.wearhouse.order.domain.dto.response.OrderCreateResponse;
import com.wearhouse.order.domain.dto.response.OrderDetailResponse;
import com.wearhouse.order.domain.dto.response.OrderDetailResponse.OrderItemDetailResponse;
import com.wearhouse.order.domain.dto.response.OrderSummaryResponse;
import com.wearhouse.order.domain.service.command.OrderCommandService;
import com.wearhouse.order.domain.service.query.OrderQueryService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderController.class)
@AutoConfigureRestDocs
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalResponseBodyAdvice.class, GlobalExceptionHandler.class})
class OrderControllerDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderCommandService orderCommandService;

    @MockitoBean
    private OrderQueryService orderQueryService;

    @Test
    @DisplayName("주문 생성 API 문서화")
    void createOrder() throws Exception {
        OrderCreateRequest request = OrderCreateRequest.builder()
                .buyerId(1L)
                .paymentMethod("CARD")
                .recipientName("홍길동")
                .recipientPhone("01012345678")
                .zipCode("06236")
                .address1("서울시 강남구")
                .address2("101동 101호")
                .deliveryRequest("문 앞에 놓아주세요")
                .shippingFee(new BigDecimal("3000"))
                .discountAmount(new BigDecimal("1000"))
                .pointUsedAmount(BigDecimal.ZERO)
                .items(List.of(OrderCreateItemRequest.builder()
                        .productId(1001L)
                        .optionId(2001L)
                        .sellerId(3001L)
                        .productName("오프화이트 티셔츠")
                        .optionName("BLACK / L")
                        .unitPrice(new BigDecimal("29000"))
                        .quantity(2)
                        .build()))
                .build();

        OrderCreateResponse response = OrderCreateResponse.builder()
                .orderId(10L)
                .orderNo("O202603060001")
                .status("PENDING_RESERVE")
                .payAmount(new BigDecimal("60000"))
                .sagaId("SAGA01TEST0123456789012345")
                .outboxEventId("OUTB01TEST0123456789012345")
                .orderedAt(LocalDateTime.of(2026, 3, 6, 12, 0, 0))
                .build();
        given(orderCommandService.createOrder(any(OrderCreateRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andDo(document("order-create",
                        getDocumentRequest(),
                        getDocumentResponse(),
                        requestFields(
                                fieldWithPath("buyerId").type(JsonFieldType.NUMBER).description("구매자 ID"),
                                fieldWithPath("paymentMethod").type(JsonFieldType.STRING).description("결제 수단"),
                                fieldWithPath("recipientName").type(JsonFieldType.STRING).description("수령인 이름"),
                                fieldWithPath("recipientPhone").type(JsonFieldType.STRING).description("수령인 연락처"),
                                fieldWithPath("zipCode").type(JsonFieldType.STRING).description("우편번호"),
                                fieldWithPath("address1").type(JsonFieldType.STRING).description("기본 주소"),
                                fieldWithPath("address2").type(JsonFieldType.STRING).optional().description("상세 주소"),
                                fieldWithPath("deliveryRequest").type(JsonFieldType.STRING).optional().description("배송 요청사항"),
                                fieldWithPath("shippingFee").type(JsonFieldType.NUMBER).optional().description("배송비"),
                                fieldWithPath("discountAmount").type(JsonFieldType.NUMBER).optional().description("할인 금액"),
                                fieldWithPath("pointUsedAmount").type(JsonFieldType.NUMBER).optional().description("포인트 사용 금액"),
                                fieldWithPath("items[].productId").type(JsonFieldType.NUMBER).description("상품 ID"),
                                fieldWithPath("items[].optionId").type(JsonFieldType.NUMBER).optional().description("옵션 ID"),
                                fieldWithPath("items[].sellerId").type(JsonFieldType.NUMBER).description("판매자 ID"),
                                fieldWithPath("items[].productName").type(JsonFieldType.STRING).description("상품명 스냅샷"),
                                fieldWithPath("items[].optionName").type(JsonFieldType.STRING).optional().description("옵션명 스냅샷"),
                                fieldWithPath("items[].unitPrice").type(JsonFieldType.NUMBER).description("단가"),
                                fieldWithPath("items[].quantity").type(JsonFieldType.NUMBER).description("수량")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("data.orderId").type(JsonFieldType.NUMBER).description("주문 ID"),
                                fieldWithPath("data.orderNo").type(JsonFieldType.STRING).description("주문 번호"),
                                fieldWithPath("data.status").type(JsonFieldType.STRING).description("주문 상태"),
                                fieldWithPath("data.payAmount").type(JsonFieldType.NUMBER).description("결제 금액"),
                                fieldWithPath("data.sagaId").type(JsonFieldType.STRING).description("Saga ID"),
                                fieldWithPath("data.outboxEventId").type(JsonFieldType.STRING).description("Outbox 이벤트 ID"),
                                fieldWithPath("data.orderedAt").type(JsonFieldType.STRING).description("주문 시각"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시각")
                        )
                ));
    }

    @Test
    @DisplayName("주문 상세 조회 API 문서화")
    void getOrder() throws Exception {
        OrderDetailResponse response = OrderDetailResponse.builder()
                .orderNo("O202603060001")
                .buyerId(1L)
                .status("CONFIRMED")
                .failReasonCode(null)
                .retryable(false)
                .nextAction("VIEW_ORDER")
                .paymentMethod("CARD")
                .recipientName("홍길동")
                .recipientPhone("01012345678")
                .zipCode("06236")
                .address1("서울시 강남구")
                .address2("101동 101호")
                .deliveryRequest("문 앞에 놓아주세요")
                .itemAmount(new BigDecimal("58000"))
                .shippingFee(new BigDecimal("3000"))
                .discountAmount(new BigDecimal("1000"))
                .pointUsedAmount(BigDecimal.ZERO)
                .payAmount(new BigDecimal("60000"))
                .orderedAt(LocalDateTime.of(2026, 3, 6, 12, 0, 0))
                .items(List.of(OrderItemDetailResponse.builder()
                        .productId(1001L)
                        .optionId(2001L)
                        .productName("오프화이트 티셔츠")
                        .optionName("BLACK / L")
                        .unitPrice(new BigDecimal("29000"))
                        .quantity(2)
                        .lineAmount(new BigDecimal("58000"))
                        .status("CONFIRMED")
                        .build()))
                .build();
        given(orderQueryService.getOrderDetail(eq("O202603060001"))).willReturn(response);

        mockMvc.perform(get("/api/v1/orders/{orderNo}", "O202603060001"))
                .andExpect(status().isOk())
                .andDo(document("order-get",
                        getDocumentRequest(),
                        getDocumentResponse(),
                        pathParameters(
                                parameterWithName("orderNo").description("주문 번호")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("data.orderNo").type(JsonFieldType.STRING).description("주문 번호"),
                                fieldWithPath("data.buyerId").type(JsonFieldType.NUMBER).description("구매자 ID"),
                                fieldWithPath("data.status").type(JsonFieldType.STRING).description("주문 상태"),
                                fieldWithPath("data.failReasonCode").type(JsonFieldType.STRING).optional().description("실패 사유 코드"),
                                fieldWithPath("data.retryable").type(JsonFieldType.BOOLEAN).description("주문서 재진입 가능 여부"),
                                fieldWithPath("data.nextAction").type(JsonFieldType.STRING).description("프론트 다음 액션"),
                                fieldWithPath("data.paymentMethod").type(JsonFieldType.STRING).description("결제 수단"),
                                fieldWithPath("data.recipientName").type(JsonFieldType.STRING).description("수령인 이름"),
                                fieldWithPath("data.recipientPhone").type(JsonFieldType.STRING).description("수령인 연락처"),
                                fieldWithPath("data.zipCode").type(JsonFieldType.STRING).description("우편번호"),
                                fieldWithPath("data.address1").type(JsonFieldType.STRING).description("기본 주소"),
                                fieldWithPath("data.address2").type(JsonFieldType.STRING).optional().description("상세 주소"),
                                fieldWithPath("data.deliveryRequest").type(JsonFieldType.STRING).optional().description("배송 요청사항"),
                                fieldWithPath("data.itemAmount").type(JsonFieldType.NUMBER).description("상품 금액"),
                                fieldWithPath("data.shippingFee").type(JsonFieldType.NUMBER).description("배송비"),
                                fieldWithPath("data.discountAmount").type(JsonFieldType.NUMBER).description("할인 금액"),
                                fieldWithPath("data.pointUsedAmount").type(JsonFieldType.NUMBER).description("포인트 사용 금액"),
                                fieldWithPath("data.payAmount").type(JsonFieldType.NUMBER).description("결제 금액"),
                                fieldWithPath("data.orderedAt").type(JsonFieldType.STRING).description("주문 시각"),
                                fieldWithPath("data.items[].productId").type(JsonFieldType.NUMBER).description("상품 ID"),
                                fieldWithPath("data.items[].optionId").type(JsonFieldType.NUMBER).optional().description("옵션 ID"),
                                fieldWithPath("data.items[].productName").type(JsonFieldType.STRING).description("상품명"),
                                fieldWithPath("data.items[].optionName").type(JsonFieldType.STRING).optional().description("옵션명"),
                                fieldWithPath("data.items[].unitPrice").type(JsonFieldType.NUMBER).description("단가"),
                                fieldWithPath("data.items[].quantity").type(JsonFieldType.NUMBER).description("수량"),
                                fieldWithPath("data.items[].lineAmount").type(JsonFieldType.NUMBER).description("라인 금액"),
                                fieldWithPath("data.items[].status").type(JsonFieldType.STRING).description("라인 상태"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시각")
                        )
                ));
    }

    @Test
    @DisplayName("주문 목록 조회 API 문서화")
    void getOrders() throws Exception {
        given(orderQueryService.getBuyerOrders(eq(1L), eq(20))).willReturn(List.of(
                OrderSummaryResponse.builder()
                        .orderNo("O202603060001")
                        .status("CONFIRMED")
                        .payAmount(new BigDecimal("60000"))
                        .orderedAt(LocalDateTime.of(2026, 3, 6, 12, 0, 0))
                        .build()
        ));

        mockMvc.perform(get("/api/v1/orders")
                        .queryParam("buyerId", "1")
                        .queryParam("limit", "20"))
                .andExpect(status().isOk())
                .andDo(document("order-list",
                        getDocumentRequest(),
                        getDocumentResponse(),
                        queryParameters(
                                parameterWithName("buyerId").description("구매자 ID"),
                                parameterWithName("limit").optional().description("조회 건수(기본 20)")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("data[].orderNo").type(JsonFieldType.STRING).description("주문 번호"),
                                fieldWithPath("data[].status").type(JsonFieldType.STRING).description("주문 상태"),
                                fieldWithPath("data[].payAmount").type(JsonFieldType.NUMBER).description("결제 금액"),
                                fieldWithPath("data[].orderedAt").type(JsonFieldType.STRING).description("주문 시각"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시각")
                        )
                ));
    }

    @Test
    @DisplayName("주문 취소 API 문서화")
    void cancelOrder() throws Exception {
        OrderCancelRequest request = OrderCancelRequest.builder()
                .reasonCode("BUYER_CHANGED_MIND")
                .build();

        OrderCancelResponse response = OrderCancelResponse.builder()
                .orderNo("O202603060001")
                .status("CANCELLED")
                .reasonCode("BUYER_CHANGED_MIND")
                .cancelledAt(LocalDateTime.of(2026, 3, 6, 13, 30, 0))
                .build();

        given(orderCommandService.cancelOrder(eq("O202603060001"), any(OrderCancelRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/v1/orders/{orderNo}/cancel", "O202603060001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andDo(document("order-cancel",
                        getDocumentRequest(),
                        getDocumentResponse(),
                        pathParameters(
                                parameterWithName("orderNo").description("주문 번호")
                        ),
                        requestFields(
                                fieldWithPath("reasonCode").type(JsonFieldType.STRING).optional().description("취소 사유 코드")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("data.orderNo").type(JsonFieldType.STRING).description("주문 번호"),
                                fieldWithPath("data.status").type(JsonFieldType.STRING).description("주문 상태"),
                                fieldWithPath("data.reasonCode").type(JsonFieldType.STRING).description("취소 사유 코드"),
                                fieldWithPath("data.cancelledAt").type(JsonFieldType.STRING).description("취소 시각"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시각")
                        )
                ));
    }
}
