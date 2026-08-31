package org.pms.silverocean.service.soko;

import org.pms.silverocean.database.pms.entities.SokoOrder;
import org.pms.silverocean.database.pms.entities.SokoOrderItem;
import org.pms.silverocean.database.pms.entities.SokoProduct;
import org.pms.silverocean.database.pms.entities.SokoStore;

import java.util.List;

public final class SokoModels {
    private SokoModels() {}

    public record CatalogProduct(SokoProduct product, String storeName, boolean deliveryEnabled, boolean pickupEnabled,
                                 Double distanceKm, List<String> imageUrls) {}
    public record ProductImages(long productId, List<String> imageUrls) {}
    public record StoreDetail(SokoStore store, List<SokoProduct> products) {}
    public record OrderDetail(SokoOrder order, String storeName, Long paymentAccountId, String paymentChannel, List<SokoOrderItem> items) {}
}
