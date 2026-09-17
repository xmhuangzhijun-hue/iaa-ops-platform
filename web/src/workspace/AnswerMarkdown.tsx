import Markdown, { defaultUrlTransform, type Components } from "react-markdown";
import remarkGfm from "remark-gfm";

/** 模型内容不可信：保留安全 Markdown，不执行 HTML，也不请求图片资源。 */
export function safeAnswerUrl(value: string): string {
  const safe = defaultUrlTransform(value);
  if (!safe) return "";
  try {
    const url = new URL(safe, "https://localhost");
    return url.protocol === "https:" || url.protocol === "http:" ? safe : "";
  } catch {
    return "";
  }
}

const components: Components = {
  a: ({ href, children }) => href
    ? <a href={href} target="_blank" rel="noopener noreferrer">{children}</a>
    : <span>{children}</span>,
  img: ({ alt }) => <span className="agent-image-omitted">[图片未加载{alt ? `：${alt}` : ""}]</span>,
  table: ({ children }) => <div className="agent-markdown-table" role="region" aria-label="助手结果表格，可横向滚动" tabIndex={0}><table>{children}</table></div>,
};

export function AnswerMarkdown({ children }: { children: string }) {
  return <div className="agent-markdown"><Markdown remarkPlugins={[remarkGfm]} skipHtml urlTransform={safeAnswerUrl} components={components}>{children}</Markdown></div>;
}
