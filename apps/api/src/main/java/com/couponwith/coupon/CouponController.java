package com.couponwith.coupon;

import com.couponwith.audit.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
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
    @GetMapping("/coupons/{couponId}/history") List<AuditService.AuditView> history(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID couponId){return service.history(userId(jwt),couponId);}
    @PostMapping("/coupons/{couponId}/correct") CouponService.CouponView correct(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID couponId,@Valid @RequestBody CorrectionRequest request){return service.correct(userId(jwt),couponId,request.status(),request.reason());}
    @PostMapping(value="/coupons/{couponId}/image",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED) CouponService.ImageView uploadImage(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID couponId,@RequestPart("file") MultipartFile file){return service.uploadImage(userId(jwt),couponId,file);}
    @GetMapping("/coupon-images/{imageId}/content") ResponseEntity<Resource> image(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID imageId){var image=service.image(userId(jwt),imageId);return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType())).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.inline().filename(image.filename(),StandardCharsets.UTF_8).build().toString()).header(HttpHeaders.CACHE_CONTROL,"private, max-age=300").body(image.resource());}
    @DeleteMapping("/coupons/{couponId}/image") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteImage(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID couponId){service.deleteImage(userId(jwt),couponId);}
    private UUID userId(Jwt jwt){return UUID.fromString(jwt.getSubject());}
    record CouponRequest(@NotBlank @Size(max=120) String title,@NotBlank @Size(max=80) String brand,@Size(max=2000) String description,@NotNull Instant expiresAt,@Size(max=500) String barcodeValue,@Size(max=30) String barcodeFormat){CouponService.CouponInput input(){return new CouponService.CouponInput(title,brand,description,expiresAt,barcodeValue,barcodeFormat);}}
    record CorrectionRequest(@NotNull CouponStatus status,@NotBlank @Size(min=5,max=500) String reason){}
}
