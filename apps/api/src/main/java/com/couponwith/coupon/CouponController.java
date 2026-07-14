package com.couponwith.coupon;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/api/v1")
public class CouponController {
    private final CouponService service; public CouponController(CouponService service){this.service=service;}
    @GetMapping("/spaces/{spaceId}/coupons") List<CouponService.CouponView> list(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID spaceId,@RequestParam(required=false) CouponStatus status,@RequestParam(required=false) String query){return service.list(userId(jwt),spaceId,status,query);}
    @PostMapping("/spaces/{spaceId}/coupons") @ResponseStatus(HttpStatus.CREATED) CouponService.CouponView create(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID spaceId,@Valid @RequestBody CouponRequest request){return service.create(userId(jwt),spaceId,request.input());}
    @PatchMapping("/coupons/{couponId}") CouponService.CouponView update(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID couponId,@Valid @RequestBody CouponRequest request){return service.update(userId(jwt),couponId,request.input());}
    @DeleteMapping("/coupons/{couponId}") @ResponseStatus(HttpStatus.NO_CONTENT) void delete(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID couponId){service.delete(userId(jwt),couponId);}
    @PostMapping("/coupons/{couponId}/claim") CouponService.CouponView claim(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID couponId){return service.claim(userId(jwt),couponId);}
    @PostMapping("/coupons/{couponId}/release") CouponService.CouponView release(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID couponId){return service.release(userId(jwt),couponId);}
    @PostMapping("/coupons/{couponId}/use") CouponService.CouponView use(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID couponId){return service.use(userId(jwt),couponId);}
    @GetMapping("/coupons/{couponId}/barcode") CouponService.BarcodeView barcode(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID couponId){return service.barcode(userId(jwt),couponId);}
    private UUID userId(Jwt jwt){return UUID.fromString(jwt.getSubject());}
    record CouponRequest(@NotBlank @Size(max=120) String title,@NotBlank @Size(max=80) String brand,@Size(max=2000) String description,@NotNull Instant expiresAt,@NotBlank @Size(max=500) String barcodeValue,@NotBlank @Size(max=30) String barcodeFormat){CouponService.CouponInput input(){return new CouponService.CouponInput(title,brand,description,expiresAt,barcodeValue,barcodeFormat);}}
}
