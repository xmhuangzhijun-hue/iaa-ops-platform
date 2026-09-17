# 首次公开 CI 验证

2026-09-17 首次推送后的 [运行 35212444191](https://github.com/xmhuangzhijun-hue/iaa-ops-platform/actions/runs/35212444191) 在调度任何作业之前失败：工作流第 81 行在 job 级 `env` 使用了 `runner.temp`，该位置不支持 `runner` context。

预期是四个作业实际启动，parity 服务日志写入 runner 临时目录，并由 artifact 步骤上传。

修正仅把 `PARITY_LOG_DIR` 移到运行对照脚本的 step 级 `env`；artifact 的 step `with.path` 保持同一路径。依照 [GitHub context availability](https://docs.github.com/en/actions/reference/workflows-and-actions/contexts#context-availability)，这两个 step 位置均支持 `runner`。没有降低测试要求、关闭作业或改变业务代码。

修改后的实际远端结果将在本记录追加；本机等价命令不能替代 GitHub 工作流解析与执行证据。
