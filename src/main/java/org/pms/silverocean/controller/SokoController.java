package org.pms.silverocean.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.soko.SokoRequests;
import org.pms.silverocean.service.soko.SokoService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/soko")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class SokoController {
    private final SokoService service;
    private final I18NService i18n;

    private ResponseEntity<ResponseDTO> ok(Object data){return ResponseEntity.ok(new ResponseDTO(true,ResponseCode.GENERAL_SUCCESS.getCode(),i18n.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS),data));}
    @GetMapping("/catalog") @PreAuthorize("permitAll()") public ResponseEntity<ResponseDTO> catalog(Pageable pageable,@RequestParam(required=false)Long storeId,@RequestParam(required=false)String category,@RequestParam(required=false)String query,@RequestParam(required=false)Double latitude,@RequestParam(required=false)Double longitude,@RequestParam(required=false)Double radiusKm){var p=service.catalog(pageable,storeId,category,query,latitude,longitude,radiusKm);return page(p);}
    @GetMapping("/catalog/{storeId}") @PreAuthorize("permitAll()") public ResponseEntity<ResponseDTO> store(@PathVariable long storeId){return ok(service.storeDetail(storeId));}
    @PostMapping("/store") public ResponseEntity<ResponseDTO> createStore(@RequestBody @Valid SokoRequests.StoreUpsert r){return ok(service.createStore(r));}
    @PutMapping("/store/{id}") public ResponseEntity<ResponseDTO> updateStore(@PathVariable long id,@RequestBody @Valid SokoRequests.StoreUpsert r){return ok(service.updateStore(id,r));}
    @PutMapping("/store/{id}/publish") public ResponseEntity<ResponseDTO> publishStore(@PathVariable long id){return ok(service.publishStore(id));}
    @GetMapping("/store/my") public ResponseEntity<ResponseDTO> myStores(){return ok(service.myStores());}
    @PostMapping("/product") public ResponseEntity<ResponseDTO> createProduct(@RequestBody @Valid SokoRequests.ProductUpsert r){return ok(service.createProduct(r));}
    @PutMapping("/product/{id}") public ResponseEntity<ResponseDTO> updateProduct(@PathVariable long id,@RequestBody @Valid SokoRequests.ProductUpsert r){return ok(service.updateProduct(id,r));}
    @PutMapping("/product/{id}/publish") public ResponseEntity<ResponseDTO> publishProduct(@PathVariable long id){return ok(service.publishProduct(id));}
    @GetMapping("/product/my") public ResponseEntity<ResponseDTO> myProducts(@RequestParam long storeId){return ok(service.myProducts(storeId));}
    @PutMapping(value="/product/{id}/images",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public ResponseEntity<ResponseDTO> productImages(@PathVariable long id,@RequestPart("images") List<MultipartFile> images)throws IOException{return ok(service.replaceProductImages(id,images));}
    @GetMapping("/product/{id}/images") public ResponseEntity<ResponseDTO> productImages(@PathVariable long id){return ok(service.productImages(id));}
    @PostMapping("/rider") public ResponseEntity<ResponseDTO> createRider(@RequestBody @Valid SokoRequests.RiderUpsert r){return ok(service.createRider(r));}
    @PutMapping("/rider/{id}") public ResponseEntity<ResponseDTO> updateRider(@PathVariable long id,@RequestBody @Valid SokoRequests.RiderUpsert r){return ok(service.updateRider(id,r));}
    @GetMapping("/rider/my") public ResponseEntity<ResponseDTO> myRiders(@RequestParam long storeId){return ok(service.myRiders(storeId));}
    @PutMapping("/rider/{id}/availability") public ResponseEntity<ResponseDTO> riderAvailability(@PathVariable long id,@RequestParam String availability){return ok(service.setRiderAvailability(id,availability));}
    @DeleteMapping("/rider/{id}") public ResponseEntity<ResponseDTO> removeRider(@PathVariable long id){service.removeRider(id);return ok(null);}
    @PostMapping("/order/checkout") public ResponseEntity<ResponseDTO> checkout(@RequestHeader(value="Idempotency-Key",required=false) String idempotencyKey,@RequestBody @Valid SokoRequests.Checkout r){return ok(service.checkout(r,idempotencyKey));}
    @GetMapping("/order/my") public ResponseEntity<ResponseDTO> myOrders(Pageable pageable){return page(service.myOrders(pageable));}
    @GetMapping("/order/merchant") public ResponseEntity<ResponseDTO> merchantOrders(Pageable pageable){return page(service.merchantOrders(pageable));}
    @GetMapping("/order/{id}/delivery-code") public ResponseEntity<ResponseDTO> deliveryCode(@PathVariable long id){return ok(service.deliveryCode(id));}
    @PutMapping("/order/{id}/delivery/confirm") public ResponseEntity<ResponseDTO> confirmDelivery(@PathVariable long id,@RequestBody @Valid SokoRequests.DeliveryConfirmation request){return ok(service.confirmDelivery(id,request));}
    @PutMapping(value="/order/{id}/delivery/proof",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public ResponseEntity<ResponseDTO> proof(@PathVariable long id,@RequestPart("proof") MultipartFile proof)throws IOException{return ok(service.uploadDeliveryProof(id,proof));}
    @GetMapping("/order/{id}/delivery/proof") public ResponseEntity<ResponseDTO> proof(@PathVariable long id){return ok(service.deliveryProof(id));}
    @PutMapping("/order/{id}/status") public ResponseEntity<ResponseDTO> status(@PathVariable long id,@RequestParam String status,@RequestBody(required=false) @Valid SokoRequests.Dispatch dispatch){return ok(service.transition(id,status,dispatch));}
    @PutMapping("/order/{id}/cancel") public ResponseEntity<ResponseDTO> cancel(@PathVariable long id,@RequestBody @Valid SokoRequests.Cancellation request){return ok(service.cancel(id,request));}
    @PutMapping("/order/{id}/finance") public ResponseEntity<ResponseDTO> finance(@PathVariable long id,@RequestBody @Valid SokoRequests.FinanceUpdate request){return ok(service.finance(id,request));}
    private ResponseEntity<ResponseDTO> page(Page<?> p){return ResponseEntity.ok(new ResponseDTO(true,ResponseCode.GENERAL_SUCCESS.getCode(),i18n.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS),p.getContent(),p.getTotalPages(),p.getTotalElements(),p.getSize()));}
}
