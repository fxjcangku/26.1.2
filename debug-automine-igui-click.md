# Debug Session: automine-igui-click
- **Status**: [OPEN]
- **Issue**: 自动挖矿在配置完成并启动、传送后打开 IGUI 时，未点击配置的关键词。
- **Debug Server**: 待启动
- **Log File**: 待创建

## Reproduction Steps
1. 配置自动挖矿关键词。
2. 启动自动挖矿。
3. 等待传送并打开 IGUI。
4. 检查是否点击关键词。

## Hypotheses & Verification
| ID | Hypothesis | Likelihood | Effort | Evidence |
|----|------------|------------|--------|----------|
| A | 关键词配置读取为空或未传递到点击流程 | High | Low | Pending |
| B | IGUI 未完成初始化即开始检索/点击 | High | Low | Pending |
| C | 控件文本或组件路径匹配失败 | Med | Low | Pending |
| D | 点击事件被界面状态、焦点或调度阻断 | Med | Med | Pending |
| E | 启动流程未进入关键词点击分支 | Med | Low | Pending |

## Log Evidence
待采集。

## Verification Conclusion
待采集运行时证据。
