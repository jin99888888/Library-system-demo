# 小型图书馆管理系统

面向小型图书馆（社区/学校/科室资料室）的轻量级图书借还管理系统。围绕「图书 → 读者 → 借出 → 归还」闭环，提供最必要的管理能力，界面简洁、单机即可部署。

## 功能

- **概览看板**：书目种类、藏书册数、读者人数、在借册数、逾期未还数量，并列出最近逾期的 5 条记录。
- **图书管理**：新增/编辑/删除图书，记录 ISBN、作者、分类、出版社、馆藏位置与总册数；可借册数随借还自动变化；支持按书名/作者/ISBN 搜索与分页。
- **读者管理**：登记/编辑/删除读者，记录借书证号（唯一）、姓名、电话、状态（正常/停用）与可借上限；支持按证号/姓名/电话搜索与分页。
- **借还管理**：选择读者与图书办理借出，按状态（全部/在借/逾期/已归还）筛选，一键还书；自动计算应还时间与逾期天数。
- **借阅规则**：可借册数大于 0、读者状态正常、单人在借数不超过其可借上限（默认 5 册）、同一本书未归还时不能重复借阅、借期默认 30 天。
- **数据保护**：存在未归还借阅的图书/读者不能删除；存在历史借阅记录时也不能删除（读者可改为「停用」，图书可保留归档），以保证借阅历史可追溯。

## 技术栈

| 层次 | 选择 |
| --- | --- |
| 语言 | Java 21 |
| 框架 | Spring Boot 3.5.16（Web / Data JPA / Security / Validation） |
| 视图 | Thymeleaf 服务端渲染 + 原生 CSS |
| 数据库 | SQLite（文件库，免安装） |
| 测试 | JUnit 5 + Spring Boot Test + MockMvc + AssertJ |

## 快速开始

前置条件：JDK 21、Maven 3.9+。

```bash
# 启动（默认端口 8080，数据库文件为 data/library.db）
mvn spring-boot:run
```

浏览器打开 <http://localhost:8080>。

首次启动会自动创建管理员账号：

- 用户名：`admin`
- 密码：`admin123`（生产使用请通过配置项 `library.admin.password` 修改后再初始化数据库）

登录后即可在「图书」「读者」「借还」三个页面完成日常操作。

## 配置

配置位于 `src/main/resources/application.yml`：

| 配置项 | 说明 | 默认值 |
| --- | --- | --- |
| `server.port` | 服务端口 | `8080` |
| `spring.datasource.url` | SQLite 数据库地址 | `jdbc:sqlite:data/library.db?...` |
| `library.loan.period-days` | 借期（天） | `30` |
| `library.admin.username` | 初始管理员用户名 | `admin` |
| `library.admin.password` | 初始管理员密码 | `admin123` |

数据文件位于工作目录下的 `data/library.db`（已被 `.gitignore` 忽略），备份该文件即备份全部数据。

## 测试

```bash
mvn test
```

测试覆盖：

- `BookServiceTest`：图书新增/修改/删除规则、库存重算、搜索与分页。
- `ReaderServiceTest`：读者登记、证号唯一性、停用后不可借、删除保护、搜索。
- `LoanServiceTest`：借书规则（无库存/停用/超上限/重复借阅）、还书与库存恢复、逾期天数、状态筛选。
- `WebFlowTest`：登录鉴权、表单校验、端到端闭环（新增图书 → 登记读者 → 借出 → 还书）与业务错误提示。

## 目录结构

```
src/main/java/com/example/library
├── config/        # 安全配置、初始化管理员
├── domain/        # 实体：Book、Reader、Loan、UserAccount 及枚举
├── repository/    # Spring Data JPA 仓储
├── service/       # 业务逻辑与 BusinessException
└── web/           # Controller 与全局异常处理
src/main/resources
├── templates/     # Thymeleaf 页面（books/readers/loans/login/index/error）
├── static/css/    # 样式
└── application.yml
src/test/java/com/example/library
├── service/       # 业务层测试
└── web/           # Web 层端到端测试
```

## 暂不支持（后续可扩展）

预约排队、罚款与支付、扫码枪录入、多馆区/多分馆、读者自助端、短信/邮件通知、批量导入导出、细粒度权限与操作审计、统计报表。

## 开发约定

见 [`AGENTS.md`](AGENTS.md)：每次改动需提交对应 Git commit，并补充/更新测试、确保全部通过后再交付。
