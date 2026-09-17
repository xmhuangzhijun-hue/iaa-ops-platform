"""报表可用的分组维度及展示名。"""

DIMENSIONS: dict[str, str] = {
    "stat_date": "日期",
    "stat_hour": "小时",
    "media": "媒体",
    "product": "产品",
    "agency": "代理",
    "account": "账户",
    "operator": "运营",
    "campaign": "推广计划",
}

MEDIA_LABELS: dict[str, str] = {
    "vivo": "vivo",
    "oppo": "OPPO",
    "huawei": "华为",
    "xiaomi": "小米",
    "honor": "荣耀",
}

UNMAPPED_LABEL = "未映射"
