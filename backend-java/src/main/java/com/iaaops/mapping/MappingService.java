package com.iaaops.mapping;

import com.iaaops.governance.AuditLog;
import com.iaaops.iam.domain.CurrentUser;
import com.iaaops.mapping.persistence.AccountMappingEntity;
import com.iaaops.mapping.persistence.AccountMappingRepository;
import com.iaaops.shared.error.ApiException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 账户归属映射的查询与批量写入。
 *
 * 写入是全有或全无：任何一条的 revision 对不上，整批都不写。半批生效比整批失败更难收拾——
 * 调用方无法从结果里知道哪几条落了地。
 */
@Service
public class MappingService {

    private final AccountMappingRepository mappings;
    private final AuditLog audit;

    MappingService(AccountMappingRepository mappings, AuditLog audit) {
        this.mappings = mappings;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page list(CurrentUser user, String media, String keyword, int page, int pageSize) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        List<Mapping> items = mappings
                .page(user.tenantId(), media, normalizedKeyword, PageRequest.of(page - 1, pageSize))
                .stream().map(MappingService::toMapping).toList();
        return new Page(items, mappings.countMatching(user.tenantId(), media, normalizedKeyword));
    }

    @Transactional
    public UpsertResult upsert(CurrentUser user, List<MappingInput> inputs) {
        rejectDuplicates(inputs);

        int created = 0;
        int updated = 0;
        int unchanged = 0;

        for (MappingInput input : inputs) {
            Optional<AccountMappingEntity> existing =
                    mappings.lock(user.tenantId(), input.media(), input.account());

            if (existing.isEmpty()) {
                if (input.revision() != null) {
                    throw ApiException.conflict("映射已被删除或从未存在",
                            input.media() + " / " + input.account() + "：请刷新后重试");
                }
                mappings.save(new AccountMappingEntity(user.tenantId(), input.media(), input.account(),
                        input.agency(), input.product(), input.operator()));
                record(user, input, null);
                created++;
                continue;
            }

            AccountMappingEntity entity = existing.get();
            if (input.revision() == null) {
                throw ApiException.conflict("映射已存在",
                        input.media() + " / " + input.account() + "：要修改请带上最后读到的 revision");
            }
            if (!input.revision().equals(entity.getRevision())) {
                throw ApiException.conflict("映射已在别处修改",
                        input.media() + " / " + input.account() + "：当前 revision 为 " + entity.getRevision());
            }
            if (entity.sameAs(input.agency(), input.product(), input.operator())) {
                unchanged++;
                continue;
            }
            record(user, input, entity);
            entity.apply(input.agency(), input.product(), input.operator());
            updated++;
        }
        return new UpsertResult(created, updated, unchanged);
    }

    /** 同一批里出现同一个账户两次：无法定义先后，直接拒绝而不是让后一条赢。 */
    private static void rejectDuplicates(List<MappingInput> inputs) {
        Set<String> seen = new LinkedHashSet<>();
        for (MappingInput input : inputs) {
            if (!seen.add(input.media() + "/" + input.account())) {
                throw ApiException.validation("同一批里重复出现：" + input.media() + " / " + input.account());
            }
        }
    }

    /**
     * 每条被改动的映射记一条审计，而不是整批记一条。
     *
     * 一条一记才能按账户查"这个账户的归属被谁改过"；整批一条的话，查某个账户要先把
     * 所有批次的 changes 展开一遍。
     */
    private void record(CurrentUser user, MappingInput input, AccountMappingEntity before) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("media", input.media());
        detail.put("account", input.account());
        detail.put("created", before == null);
        detail.put("agency_before", before == null ? null : before.getAgency());
        detail.put("agency_after", input.agency());
        detail.put("product_before", before == null ? null : before.getProduct());
        detail.put("product_after", input.product());
        detail.put("operator_before", before == null ? null : before.getOperator());
        detail.put("operator_after", input.operator());
        audit.record(user, AuditLog.MAPPING_UPSERT, "account_mapping",
                input.media() + "/" + input.account(), detail);
    }

    private static Mapping toMapping(AccountMappingEntity entity) {
        return new Mapping(entity.getAccount(), entity.getMedia(), entity.getAgency(), entity.getProduct(),
                entity.getOperator(), entity.getRevision());
    }

    public record Mapping(String account, String media, String agency, String product, String operator,
            int revision) {
    }

    public record Page(List<Mapping> items, long total) {
    }

    public record MappingInput(String account, String media, String agency, String product, String operator,
            Integer revision) {
    }

    public record UpsertResult(int created, int updated, int unchanged) {
    }
}
