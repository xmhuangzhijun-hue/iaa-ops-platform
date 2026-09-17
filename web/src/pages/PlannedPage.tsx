import { EmptyBlock } from "../components/States";

export function PlannedPage({ title, description, planned = true }: { title: string; description: string; planned?: boolean }) {
  return (
    <div className="space-y-4">
      <h2 className="text-xl font-semibold tracking-tight">{title}</h2>
      <EmptyBlock title={planned ? "第 3 阶段实现" : title}>
        {description}
        {planned && " 接口契约已确定，可在接口文档中查看。"}
      </EmptyBlock>
    </div>
  );
}
