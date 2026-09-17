/**
 * 维度词表：对齐原型实测的 21 个聚合维度。
 * 接后端后由 `/filter-options` 返回，这里作为界面先行阶段的取值来源。
 */

export const VENDORS = ["vivo", "OPPO", "华为", "小米", "荣耀"] as const;

// 媒体在库里存的是代码，展示名只是给人看的：筛选、上传都必须按代码发出去
export const MEDIA_OPTIONS = [
  { value: "vivo", label: "vivo" },
  { value: "oppo", label: "OPPO" },
  { value: "huawei", label: "华为" },
  { value: "xiaomi", label: "小米" },
  { value: "honor", label: "荣耀" },
];
export const PLATFORMS = ["vivo平台", "OPPO平台", "华为平台", "小米平台", "巨量引擎"] as const;
export const SUB_PLATFORMS = ["站内", "联盟", "穿山甲", "优量汇"] as const;
export const LANDINGS = ["H5", "DP"] as const;
export const CATEGORIES = ["天气", "工具", "阅读", "休闲游戏", "生活", "相册"] as const;
export const PRODUCTS = [
  "晴空天气", "脑力答题王", "口袋清理", "极简记账", "萌宠消消乐", "每日菜谱",
  "快看小说", "步数宝", "云图相册", "静心冥想", "掌上词典", "趣味测算",
] as const;
export const AGENCIES = ["星河代理", "蓝鲸代理", "青橙代理", "自投"] as const;
export const TEAMS = ["投放一组", "投放二组", "投放三组"] as const;
export const OWNERS = ["运营甲", "运营乙", "运营丙", "运营丁"] as const;
export const CONVERT_RULES = ["投放点击ARPU0.1", "投放点击ARPU0.05", "次均ARPU0.08", "投放ARPU0.27"] as const;
export const CALLBACK_RULES = ["ROI", "概率", "阶梯ROI"] as const;
export const CALLBACK_LEVELS = ["计划级", "账户级"] as const;
export const CONVERT_TARGETS = ["激活", "次留", "付费", "关键页面"] as const;
export const FRESHNESS = ["新号", "成熟号", "衰退号"] as const;
export const COST_TYPES = ["现金", "返点", "赠款"] as const;
export const STRATEGIES = ["首日回收", "长效ROI", "跑量优先"] as const;
export const YES_NO = ["是", "否"] as const;
export const CREATIVE_TYPES = ["信息流", "开屏", "激励视频"] as const;
export const CREATIVE_STYLES = ["单图", "三图", "竖版视频", "横版视频"] as const;
export const SLOTS = ["首页信息流", "详情页", "锁屏", "开屏"] as const;

export const options = (values: readonly string[]) => values.map((value) => ({ value, label: value }));
