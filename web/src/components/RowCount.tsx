export function RowCount({ total, shown }: { total: number; shown: number }) {
  return (
    <span>
      共 <b className="text-text tabular-nums">{total.toLocaleString("zh-CN")}</b> 行
      {shown < total && <span className="text-warn">，仅显示前 {shown.toLocaleString("zh-CN")} 行，请缩小范围或导出</span>}
    </span>
  );
}
