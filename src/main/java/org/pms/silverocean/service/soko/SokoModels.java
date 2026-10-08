package org.pms.silverocean.service.soko;

import org.pms.silverocean.database.pms.entities.SokoOrder;
import org.pms.silverocean.database.pms.entities.SokoOrderItem;
import org.pms.silverocean.database.pms.entities.SokoProduct;
import org.pms.silverocean.database.pms.entities.SokoRider;
import org.pms.silverocean.database.pms.entities.SokoStore;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

public final class SokoModels {
    private SokoModels() {}

    /** Public catalogue representation. Never expose entity ownership, moderation or payment-routing fields. */
    public record PublicProduct(long id, long storeId, String name, String description, String category, String unit,
                                BigDecimal price, String currency, int stockQuantity, String imageUrl,
                                String status, String variationsJson) {
        public static PublicProduct from(SokoProduct product) {
            return new PublicProduct(product.getId(), product.getStoreId(), product.getName(), product.getDescription(),
                    product.getCategory(), product.getUnit(), product.getPrice(), product.getCurrency(),
                    product.getStockQuantity(), product.getImageUrl(), product.getStatus(), product.getVariationsJson());
        }
    }

    /** Customer-visible shop details. Internal owner and receiving-account identifiers stay private. */
    public record PublicStore(long id, String name, String description, String storePhoneNumber, String address,
                              Double latitude, Double longitude, BigDecimal serviceRadiusKm,
                              boolean pickupEnabled, boolean deliveryEnabled, BigDecimal deliveryFee,
                              String currency) {
        public static PublicStore from(SokoStore store) {
            return new PublicStore(store.getId(), store.getName(), store.getDescription(), store.getPhoneNumber(),
                    store.getAddress(), store.getLatitude(), store.getLongitude(), store.getServiceRadiusKm(),
                    store.isPickupEnabled(), store.isDeliveryEnabled(), store.getDeliveryFee(), store.getCurrency());
        }
    }

    public record CatalogProduct(PublicProduct product, String storeName, String storeAddress, String storePhoneNumber,
                                 boolean deliveryEnabled, boolean pickupEnabled, Double distanceKm,
                                 BigDecimal serviceRadiusKm, List<String> imageUrls, BigDecimal deliveryFee) {}
    public record ProductImages(long productId, List<String> imageUrls) {}
    public record StoreDetail(PublicStore store, List<PublicProduct> products) {}
    public record RiderVerificationResult(SokoRider rider, String confirmationStatus, String message) {}
    /** Buyer/merchant order view. Persistence, routing and recovery-control metadata stay server-side. */
    public record OrderView(long id, String orderNumber, long storeId, String status, String paymentStatus,
                            String invoiceRef, String deliveryMethod, String deliveryAddress,
                            Double deliveryLatitude, Double deliveryLongitude, String customerPhone, String notes,
                            Long destinationUnitId, BigDecimal subtotal, BigDecimal deliveryFee, BigDecimal total,
                            String currency, ZonedDateTime placedAt, ZonedDateTime confirmedAt,
                            ZonedDateTime dispatchedAt, ZonedDateTime completedAt, Long riderId,
                            String courierName, String courierPhone, String courierVehiclePlate,
                            boolean deliveryCodeVerified, ZonedDateTime reservationExpiresAt,
                            ZonedDateTime cancelledAt, String cancellationReason,
                            String refundStatus, BigDecimal refundedAmount, String deliveryRecipientName,
                            boolean deliveryProofUploaded,
                            ZonedDateTime deliveryProofAt, ZonedDateTime expectedArrivalAt, ZonedDateTime assignedAt,
                            ZonedDateTime assignmentAcceptedAt, ZonedDateTime collectedAt,
                            ZonedDateTime deliveryFailedAt, ZonedDateTime returnedAt, String deliveryExceptionReason,
                            ZonedDateTime deliveryRecoveryRequestedAt) {
        public static OrderView from(SokoOrder order) {
            return new OrderView(order.getId(),order.getOrderNumber(),order.getStoreId(),order.getStatus(),
                    order.getPaymentStatus(),order.getInvoiceRef(),order.getDeliveryMethod(),order.getDeliveryAddress(),
                    order.getDeliveryLatitude(),order.getDeliveryLongitude(),order.getCustomerPhone(),order.getNotes(),
                    order.getDestinationUnitId(),order.getSubtotal(),order.getDeliveryFee(),order.getTotal(),
                    order.getCurrency(),order.getPlacedAt(),order.getConfirmedAt(),order.getDispatchedAt(),
                    order.getCompletedAt(),order.getRiderId(),order.getCourierName(),order.getCourierPhone(),
                    order.getCourierVehiclePlate(),order.isDeliveryCodeVerified(),order.getReservationExpiresAt(),
                    order.getCancelledAt(),order.getCancellationReason(),
                    order.getRefundStatus(),order.getRefundedAmount(),order.getDeliveryRecipientName(),
                    order.getDeliveryProofReference()!=null,order.getDeliveryProofAt(),
                    order.getExpectedArrivalAt(),order.getAssignedAt(),order.getAssignmentAcceptedAt(),order.getCollectedAt(),
                    order.getDeliveryFailedAt(),order.getReturnedAt(),order.getDeliveryExceptionReason(),
                    order.getDeliveryRecoveryRequestedAt());
        }
    }
    public record OrderItemView(Long id, long productId, String productName, Long variationId,
                                String variationName, String variationValue, String unit,
                                BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {
        public static OrderItemView from(SokoOrderItem item) {
            return new OrderItemView(item.getId(),item.getProductId(),item.getProductName(),item.getVariationId(),
                    item.getVariationName(),item.getVariationValue(),item.getUnit(),item.getUnitPrice(),
                    item.getQuantity(),item.getLineTotal());
        }
    }
    public record OrderDetail(OrderView order, String storeName, String storeAddress, String storePhoneNumber,
                              Double storeLatitude, Double storeLongitude,
                              Long paymentAccountId, String paymentChannel, List<OrderItemView> items) {}
    /** Least-privilege delivery view: riders never receive payment, settlement or recovery-control fields. */
    public record RiderOrder(long id, String orderNumber, long storeId, String status, String deliveryMethod,
                             String deliveryAddress, Double deliveryLatitude, Double deliveryLongitude,
                             String customerPhone, String deliveryNotes, Long destinationUnitId,
                             Long riderId, String courierName, String courierPhone, String courierVehiclePlate,
                             boolean deliveryProofUploaded,
                             ZonedDateTime placedAt, ZonedDateTime confirmedAt, ZonedDateTime expectedArrivalAt,
                             ZonedDateTime assignedAt, ZonedDateTime assignmentAcceptedAt, ZonedDateTime collectedAt,
                             ZonedDateTime dispatchedAt, ZonedDateTime deliveryFailedAt, ZonedDateTime returnedAt,
                             String deliveryExceptionReason, ZonedDateTime completedAt) {
        public static RiderOrder from(SokoOrder order) {
            return new RiderOrder(order.getId(), order.getOrderNumber(), order.getStoreId(), order.getStatus(),
                    order.getDeliveryMethod(), order.getDeliveryAddress(), order.getDeliveryLatitude(), order.getDeliveryLongitude(),
                    order.getCustomerPhone(), order.getNotes(),
                    order.getDestinationUnitId(), order.getRiderId(), order.getCourierName(), order.getCourierPhone(),
                    order.getCourierVehiclePlate(), order.getDeliveryProofReference()!=null, order.getPlacedAt(), order.getConfirmedAt(), order.getExpectedArrivalAt(),
                    order.getAssignedAt(), order.getAssignmentAcceptedAt(), order.getCollectedAt(), order.getDispatchedAt(),
                    order.getDeliveryFailedAt(), order.getReturnedAt(), order.getDeliveryExceptionReason(), order.getCompletedAt());
        }
    }
    public record RiderItem(Long id, long productId, String productName, Long variationId,
                            String variationName, String variationValue, String unit, int quantity) {
        public static RiderItem from(SokoOrderItem item) {
            return new RiderItem(item.getId(), item.getProductId(), item.getProductName(), item.getVariationId(),
                    item.getVariationName(), item.getVariationValue(), item.getUnit(), item.getQuantity());
        }
    }
    public record RiderAssignment(RiderOrder order, String storeName, String storeAddress, String storePhoneNumber,
                                  Double storeLatitude, Double storeLongitude,
                                  List<RiderItem> items) {}
    /** Mutation acknowledgement shared by linked riders and merchant-managed riders. */
    public record OrderAction(long orderId, String orderNumber, String status, boolean deliveryProofUploaded,
                              ZonedDateTime assignedAt, ZonedDateTime assignmentAcceptedAt,
                              ZonedDateTime collectedAt, ZonedDateTime dispatchedAt,
                              ZonedDateTime deliveryFailedAt, ZonedDateTime returnedAt,
                              ZonedDateTime completedAt) {
        public static OrderAction from(SokoOrder order) {
            return new OrderAction(order.getId(),order.getOrderNumber(),order.getStatus(),
                    order.getDeliveryProofReference()!=null,order.getAssignedAt(),order.getAssignmentAcceptedAt(),
                    order.getCollectedAt(),order.getDispatchedAt(),order.getDeliveryFailedAt(),order.getReturnedAt(),
                    order.getCompletedAt());
        }
    }
    public record AdminSummary(long stores,long pendingStores,long publishedStores,long products,long publishedProducts,long orders,long activeOrders) {}
}
