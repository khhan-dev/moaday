package com.couponwith.coupon;

import com.couponwith.common.ApiException;
import com.couponwith.identity.UserRepository;
import com.couponwith.notification.NotificationService;
import com.couponwith.space.SpaceMember;
import com.couponwith.space.SpaceMemberRepository;
import com.couponwith.space.SpaceRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class CouponService {
    private final CouponRepository coupons; private final SpaceMemberRepository members; private final UserRepository users; private final NotificationService notifications;
    public CouponService(CouponRepository coupons,SpaceMemberRepository members,UserRepository users,NotificationService notifications){this.coupons=coupons;this.members=members;this.users=users;this.notifications=notifications;}
    @Transactional public List<CouponView> list(UUID actorId,UUID spaceId,CouponStatus status,String query){requireMembership(spaceId,actorId);var q=query==null?"":query.trim().toLowerCase(Locale.ROOT);return coupons.findBySpaceIdOrderByExpiresAt(spaceId).stream().peek(Coupon::expireIfNeeded).filter(c->status==null||c.getStatus()==status).filter(c->q.isEmpty()||c.getTitle().toLowerCase(Locale.ROOT).contains(q)||c.getBrand().toLowerCase(Locale.ROOT).contains(q)).map(c->view(c,actorId)).toList();}
    @Transactional public CouponView create(UUID actorId,UUID spaceId,CouponInput input){requireRedeemer(spaceId,actorId);var format=validate(input);var coupon=coupons.save(new Coupon(UUID.randomUUID(),spaceId,actorId,input.title().trim(),input.brand().trim(),clean(input.description()),input.expiresAt(),input.barcodeValue().trim(),format));notifications.notifySpace(spaceId,actorId,"COUPON_CREATED","새 공유 쿠폰",coupon.getTitle(),"/coupons/"+coupon.getId());return view(coupon,actorId);}
    @Transactional public CouponView update(UUID actorId,UUID couponId,CouponInput input){var coupon=lock(couponId);coupon.expireIfNeeded();requireOwnerOrManager(coupon,actorId);if(coupon.getStatus()!=CouponStatus.AVAILABLE)throw conflict("COUPON_NOT_EDITABLE","사용 가능 상태의 쿠폰만 수정할 수 있습니다.");var format=validate(input);coupon.update(input.title().trim(),input.brand().trim(),clean(input.description()),input.expiresAt(),input.barcodeValue().trim(),format);return view(coupon,actorId);}
    @Transactional public void delete(UUID actorId,UUID couponId){var coupon=lock(couponId);requireOwnerOrManager(coupon,actorId);if(coupon.getStatus()==CouponStatus.CLAIMED)throw conflict("CLAIMED_COUPON_DELETE_NOT_ALLOWED","선점된 쿠폰은 해제한 뒤 삭제해 주세요.");coupons.delete(coupon);}
    @Transactional public CouponView claim(UUID actorId,UUID couponId){var coupon=lock(couponId);requireRedeemer(coupon.getSpaceId(),actorId);coupon.expireIfNeeded();if(coupon.getStatus()==CouponStatus.EXPIRED)throw new ApiException(HttpStatus.GONE,"COUPON_EXPIRED","만료된 쿠폰입니다.");if(coupon.getStatus()!=CouponStatus.AVAILABLE)throw conflict("COUPON_ALREADY_CLAIMED","다른 구성원이 이미 선점했거나 사용한 쿠폰입니다.");coupon.claim(actorId);if(!coupon.getOwnerId().equals(actorId))notifications.notifyUser(coupon.getOwnerId(),coupon.getSpaceId(),"COUPON_CLAIMED","쿠폰 선점",coupon.getTitle()+" 쿠폰을 구성원이 선점했습니다.","/coupons/"+couponId);return view(coupon,actorId);}
    @Transactional public CouponView release(UUID actorId,UUID couponId){var coupon=lock(couponId);coupon.expireIfNeeded();requireMembership(coupon.getSpaceId(),actorId);if(coupon.getStatus()!=CouponStatus.CLAIMED)throw conflict("COUPON_NOT_CLAIMED","선점된 쿠폰이 아닙니다.");if(!actorId.equals(coupon.getClaimedBy())&&!isManager(coupon.getSpaceId(),actorId)&&!actorId.equals(coupon.getOwnerId()))throw new ApiException(HttpStatus.FORBIDDEN,"COUPON_RELEASE_NOT_ALLOWED","선점한 사람만 쿠폰을 해제할 수 있습니다.");coupon.release();return view(coupon,actorId);}
    @Transactional public CouponView use(UUID actorId,UUID couponId){var coupon=lock(couponId);coupon.expireIfNeeded();requireMembership(coupon.getSpaceId(),actorId);if(coupon.getStatus()!=CouponStatus.CLAIMED)throw conflict("COUPON_NOT_CLAIMED","먼저 쿠폰을 선점해 주세요.");if(!actorId.equals(coupon.getClaimedBy())&&!isManager(coupon.getSpaceId(),actorId)&&!actorId.equals(coupon.getOwnerId()))throw new ApiException(HttpStatus.FORBIDDEN,"COUPON_USE_NOT_ALLOWED","선점한 사람만 사용 처리할 수 있습니다.");coupon.use(actorId);if(!coupon.getOwnerId().equals(actorId))notifications.notifyUser(coupon.getOwnerId(),coupon.getSpaceId(),"COUPON_USED","쿠폰 사용 완료",coupon.getTitle()+" 쿠폰이 사용 처리되었습니다.","/coupons/"+couponId);return view(coupon,actorId);}
    @Transactional public BarcodeView barcode(UUID actorId,UUID couponId){var coupon=lock(couponId);coupon.expireIfNeeded();requireMembership(coupon.getSpaceId(),actorId);if(!canReveal(coupon,actorId))throw new ApiException(HttpStatus.FORBIDDEN,"BARCODE_NOT_ALLOWED","쿠폰을 선점한 뒤 바코드를 볼 수 있습니다.");return new BarcodeView(coupon.getId(),coupon.getBarcodeValue(),coupon.getBarcodeFormat(),coupon.getStatus());}
    private CouponView view(Coupon c,UUID actorId){String claimedName=c.getClaimedBy()==null?null:users.findById(c.getClaimedBy()).map(user->user.getDisplayName()).orElse("알 수 없음");return new CouponView(c.getId(),c.getSpaceId(),c.getTitle(),c.getBrand(),c.getDescription(),c.getExpiresAt(),c.getBarcodeFormat(),c.getStatus(),c.getOwnerId(),c.getClaimedBy(),claimedName,c.getClaimedAt(),c.getUsedBy(),c.getUsedAt(),canReveal(c,actorId),canEdit(c,actorId),c.getVersion(),c.getCreatedAt(),c.getUpdatedAt());}
    private boolean canReveal(Coupon c,UUID actorId){return actorId.equals(c.getOwnerId())||actorId.equals(c.getClaimedBy())||isManager(c.getSpaceId(),actorId);}
    private boolean canEdit(Coupon c,UUID actorId){return (actorId.equals(c.getOwnerId())||isManager(c.getSpaceId(),actorId))&&c.getStatus()==CouponStatus.AVAILABLE;}
    private String validate(CouponInput input){
        if(!input.expiresAt().isAfter(Instant.now()))throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_COUPON_EXPIRY","만료일은 현재 이후여야 합니다.");
        var format=input.barcodeFormat().trim().toUpperCase(Locale.ROOT);var value=input.barcodeValue().trim();
        if(!format.equals("CODE128")&&!format.equals("EAN13"))throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_BARCODE_FORMAT","바코드는 CODE128 또는 EAN13 형식만 사용할 수 있습니다.");
        if(format.equals("EAN13")&&!value.matches("\\d{12}|\\d{13}"))throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_EAN13","EAN13 바코드는 숫자 12자리 또는 13자리여야 합니다.");
        if(format.equals("EAN13")&&value.length()==13&&!validEan13(value))throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_EAN13_CHECKSUM","EAN13 체크 숫자가 올바르지 않습니다.");
        if(format.equals("CODE128")&&(value.length()>120||value.chars().anyMatch(character->character<32||character>126)))throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_CODE128","CODE128 값은 영문, 숫자와 일반 기호를 120자까지 사용할 수 있습니다.");
        return format;
    }
    private boolean validEan13(String value){int sum=0;for(int index=0;index<12;index++){int digit=value.charAt(index)-'0';sum+=index%2==0?digit:digit*3;}return (10-(sum%10))%10==value.charAt(12)-'0';}
    private void requireOwnerOrManager(Coupon c,UUID actorId){requireMembership(c.getSpaceId(),actorId);if(!actorId.equals(c.getOwnerId())&&!isManager(c.getSpaceId(),actorId))throw new ApiException(HttpStatus.FORBIDDEN,"COUPON_MUTATION_NOT_ALLOWED","쿠폰을 수정하거나 삭제할 권한이 없습니다.");}
    private SpaceMember requireRedeemer(UUID spaceId,UUID actorId){var member=requireMembership(spaceId,actorId);if(member.getRole()==SpaceRole.VIEWER)throw new ApiException(HttpStatus.FORBIDDEN,"COUPON_ACTION_NOT_ALLOWED","열람자는 쿠폰을 등록하거나 선점할 수 없습니다.");return member;}
    private boolean isManager(UUID spaceId,UUID actorId){var member=requireMembership(spaceId,actorId);return member.getRole()==SpaceRole.OWNER||member.getRole()==SpaceRole.ADMIN;}
    private SpaceMember requireMembership(UUID spaceId,UUID actorId){return members.findBySpaceIdAndUserIdAndStatus(spaceId,actorId,"ACTIVE").orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"SPACE_NOT_FOUND","공간을 찾을 수 없습니다."));}
    private Coupon lock(UUID id){return coupons.findForUpdate(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"COUPON_NOT_FOUND","쿠폰을 찾을 수 없습니다."));}
    private ApiException conflict(String code,String message){return new ApiException(HttpStatus.CONFLICT,code,message);} private String clean(String value){return value==null||value.isBlank()?null:value.trim();}
    public record CouponInput(String title,String brand,String description,Instant expiresAt,String barcodeValue,String barcodeFormat){}
    public record CouponView(UUID id,UUID spaceId,String title,String brand,String description,Instant expiresAt,String barcodeFormat,CouponStatus status,UUID ownerId,UUID claimedBy,String claimedByName,Instant claimedAt,UUID usedBy,Instant usedAt,boolean barcodeAvailable,boolean canEdit,long version,Instant createdAt,Instant updatedAt){}
    public record BarcodeView(UUID couponId,String value,String format,CouponStatus status){}
}
