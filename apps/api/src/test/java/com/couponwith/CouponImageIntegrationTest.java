package com.couponwith;

import com.couponwith.coupon.CouponService;
import com.couponwith.identity.AuthService;
import com.couponwith.space.SpaceRole;
import com.couponwith.space.SpaceService;
import com.couponwith.space.SpaceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class CouponImageIntegrationTest {
    @Autowired AuthService authService;
    @Autowired SpaceService spaceService;
    @Autowired CouponService couponService;

    @Test
    void storesOriginalCouponImageAndAllowsSpaceMembersToViewIt() throws Exception {
        var suffix = "coupon-image-" + System.nanoTime();
        var owner = authService.register(suffix + "-owner@example.com", "password123!", "소유자", "Asia/Seoul");
        var member = authService.register(suffix + "-member@example.com", "password123!", "구성원", "Asia/Seoul");
        var space = spaceService.create(owner.user().id(), SpaceType.FAMILY, "이미지 가족", "Asia/Seoul", "green");
        var invitation = spaceService.invite(owner.user().id(), space.id(), member.user().email(), SpaceRole.MEMBER);
        spaceService.accept(member.user().id(), invitation.oneTimeToken());
        var coupon = couponService.create(owner.user().id(), space.id(), new CouponService.CouponInput(
                "원본 이미지 쿠폰", "Moa Cafe", null, Instant.now().plus(7, ChronoUnit.DAYS), null, null));
        var original = new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1, 2, 3, 4};

        var image = couponService.uploadImage(owner.user().id(), coupon.id(),
                new MockMultipartFile("file", "coupon-original.png", "image/png", original));

        assertThat(image.originalName()).isEqualTo("coupon-original.png");
        assertThat(image.contentType()).isEqualTo("image/png");
        assertThat(image.sizeBytes()).isEqualTo(original.length);
        assertThat(couponService.list(member.user().id(), space.id(), null, null).getFirst().image()).isEqualTo(image);
        assertThat(couponService.list(member.user().id(), space.id(), null, null).getFirst().hasBarcode()).isFalse();
        assertThat(couponService.list(member.user().id(), space.id(), null, null).getFirst().barcodeAvailable()).isFalse();
        assertThatThrownBy(() -> couponService.barcode(member.user().id(), coupon.id())).hasMessageContaining("등록된 바코드");
        try (var input = couponService.image(member.user().id(), image.id()).resource().getInputStream()) {
            assertThat(input.readAllBytes()).isEqualTo(original);
        }

        assertThatThrownBy(() -> couponService.uploadImage(owner.user().id(), coupon.id(),
                new MockMultipartFile("file", "coupon.txt", "text/plain", "not an image".getBytes())))
                .hasMessageContaining("JPG, PNG");

        couponService.deleteImage(owner.user().id(), coupon.id());
        assertThat(couponService.list(owner.user().id(), space.id(), null, null).getFirst().image()).isNull();
    }
}
