package com.iaaops.mapping.web;

import com.iaaops.iam.domain.CurrentUser;
import com.iaaops.iam.domain.Permissions;
import com.iaaops.mapping.MappingService;
import com.iaaops.shared.error.ApiException;
import com.iaaops.shared.error.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mappings")
public class MappingController {

    private final MappingService mappings;

    MappingController(MappingService mappings) {
        this.mappings = mappings;
    }

    @GetMapping("/accounts")
    public MappingPage list(@AuthenticationPrincipal CurrentUser user,
            @RequestParam(required = false) @Size(max = 32) String media,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(name = "page_size", defaultValue = "50") @Min(1) @Max(200) int pageSize) {
        require(user);
        MappingService.Page result = mappings.list(user, media, keyword, page, pageSize);
        return new MappingPage(result.items().stream().map(MappingController::toItem).toList(), result.total());
    }

    @PutMapping("/accounts")
    public MappingService.UpsertResult upsert(@AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody UpsertRequest request) {
        require(user);
        return mappings.upsert(user, request.items().stream()
                .map(item -> new MappingService.MappingInput(item.account(), item.media(), item.agency(),
                        item.product(), item.operator(), item.revision()))
                .toList());
    }

    private static void require(CurrentUser user) {
        if (!Permissions.has(user.permissions(), Permissions.MAPPINGS_MANAGE)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "没有该操作的权限");
        }
    }

    private static Item toItem(MappingService.Mapping mapping) {
        return new Item(mapping.account(), mapping.media(), mapping.agency(), mapping.product(),
                mapping.operator(), mapping.revision());
    }

    public record Item(String account, String media, String agency, String product, String operator, int revision) {
    }

    public record MappingPage(List<Item> items, long total) {
    }

    public record MappingInput(
            @NotBlank @Size(max = 64) String account,
            @NotBlank @Size(max = 32) String media,
            @Size(max = 64) String agency,
            @Size(max = 64) String product,
            @Size(max = 64) String operator,
            @Min(1) Integer revision) {
    }

    public record UpsertRequest(@Valid @Size(min = 1, max = 500) List<MappingInput> items) {
    }
}
