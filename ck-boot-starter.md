# JeecgBoot 架构拆分与多项目演进最佳实践指南

本文档提供了一套标准的工程化方案，用于将 **JeecgBoot** 官方单仓（Monorepo）代码重构为自定义通用脚手架，并建立“**官方仓库 $\rightarrow$ 通用脚手架 $\rightarrow$ 业务项目**”的三层单向演进架构。

## 一、 整体架构设计

通过避免业务项目直接与 JeecgBoot 官方仓库发生合并冲突，采用**单向递进**的演进模型：

Plaintext

```
Jeecg 官方仓库 (upstream)
       │ 
       │ (1. 拉取官方漏洞修复与新特性)
       ▼
ck-boot-starter (自定义通用脚手架模板)
       │ 
       │ (2. 沉淀核心组件/微服务底座，同步至各项目)
       ▼
ck-shop / ck-oa (具体业务项目)
```

### 仓库职责分工

- **`ck-boot-starter`（通用脚手架）**：
  - **`upstream`** $\rightarrow$ Jeecg 官方 GitHub 仓库
  - **`origin`** $\rightarrow$ 团队/个人私有脚手架仓库 (`ck-boot-starter.git`)
  - **职责**：同步 JeecgBoot 官方代码，引入团队通用组件（如 Logback 日志规范、OSS 文件存储封装、自定义 Redis/MQTT 校验等底座配置）。
- **`ck-shop`（具体业务项目）**：
  - **`base-template`** $\rightarrow$ 通用脚手架仓库 (`ck-boot-starter.git`)
  - **`origin`** $\rightarrow$ 团队/个人业务仓库 (`ck-shop.git`)
  - **职责**：专注具体业务功能实现，仅在脚手架底座更新时进行版本同步。

## 二、 阶段一：搭建通用脚手架 (`ck-boot-starter`)

在本地从 0 开始初始化脚手架仓库，完成目录结构的规范化重命名，并关联 JeecgBoot 官方源。

### Step 1: 初始化本地仓库并关联官方源

打开终端进入工作目录（以 `E:\CProject\` 为例）：

Bash

```
# 1. 创建并进入脚手架根目录
mkdir ck-boot-starter
cd ck-boot-starter

# 2. 初始化 Git 仓库
git init

# 3. 添加 JeecgBoot 官方仓库为 upstream
git remote add upstream https://github.com/jeecgboot/JeecgBoot.git

# 4. 拉取官方最新 main 分支到本地分支 official-upstream
git fetch upstream main:official-upstream
```

### Step 2: 目录规范化重命名

切换到官方代码分支，将默认目录结构重命名为通用规范：

Bash

```
# 1. 切换到 official-upstream 分支
git checkout official-upstream

# 2. 重命名后端与前端子目录
git mv jeecg-boot server
git mv jeecgboot-vue3 admin

# 3. 提交目录命名变更
git commit -m "chore: rename directories (jeecg-boot -> server, jeecgboot-vue3 -> admin)"

# 4. 创建并切回开发主分支 main，完成合并
git checkout -b main
git merge official-upstream --allow-unrelated-histories -m "chore: initialize ck-boot-starter baseline from official JeecgBoot"
```

### Step 3: 脚手架基础改造与工程推送

当前项目的标准目录结构如下：

Plaintext

```
ck-boot-starter/
├── admin/       # 前端管理后台 (原 jeecgboot-vue3)
├── server/      # 后端服务主工程 (原 jeecg-boot)
└── README.md
```

1. 在 `server` 与 `admin` 中引入项目通用的基础封装工具类、自定义 Starter 及 Log 规范文件。
2. 在 Git 平台（Gitee / GitLab / GitHub）创建私有仓库 `ck-boot-starter.git`。
3. 执行提交与远程关联推送：

Bash

```
# 提交本地改造代码
git add .
git commit -m "feat: add custom core logger, oss modules and base configurations"

# 关联私有仓库源并推送
git remote add origin <你的 ck-boot-starter 私有库地址.git>
git push -u origin main
```

## 三、 阶段二：派生衍生业务项目 (`ck-shop`)

当需要启动具体业务项目（如商城 `ck-shop`）时，通过 Git 继承基因，避免直接使用文件复制粘贴导致的提交历史断链。

### Step 1: 从脚手架克隆派生新项目

在 Git 平台上新建空白业务仓库 `ck-shop.git`，然后在终端执行：

Bash

```
cd E:\CProject\

# 1. 从通用脚手架 Clone 出来作为新项目目录
git clone <你的 ck-boot-starter 私有库地址.git> ck-shop

# 2. 进入新项目目录
cd ck-shop

# 3. 将原脚手架 origin 重命名为 base-template（标记为底座上游）
git remote rename origin base-template

# 4. 绑定业务项目自己的私有仓库为 origin
git remote add origin <你的 ck-shop 私有库地址.git>

# 5. 推送初始化基线到业务仓库
git push -u origin main
```

### Step 2: 业务功能开发

在 `ck-shop` 目录下，保留 `server` 和 `admin` 的基本结构，自由增加业务模块与代码。提交将直接作用于业务私有库：

Bash

```
git add .
git commit -m "feat: implement shop order management APIs"
git push origin main
```

## 四、 阶段三：长期的版本升级 SOP 流水线

当 JeecgBoot 官方发布重大更新或安全补丁时，按照“**先升脚手架，后同步业务**”的顺序进行升级。

### 流程 1：升级脚手架底座 (`ck-boot-starter`)

Bash

```
cd E:\CProject\ck-boot-starter

# 1. 切换到官方镜像分支并拉取最新官方代码
git checkout official-upstream
git pull upstream main

# 2. 检查官方是否增加了顶层目录（若有，再次使用 git mv 对齐目录名），然后切回 main
git checkout main

# 3. 合并官方分支更新
git merge official-upstream -m "merge: sync latest official JeecgBoot updates"

# 4. 在 IDE 中完成冲突解决后提交，并推送到脚手架私有库
git push origin main
```

### 流程 2：业务项目 (`ck-shop`) 吸收底座升级

Bash

```
cd E:\CProject\ck-shop

# 1. 获取脚手架仓库的最新变更
git fetch base-template

# 2. 将脚手架最新的 main 分支合并到业务项目中
git merge base-template/main -m "merge: sync core architecture updates from ck-boot-starter"

# 3. 在 IDE 中解决业务与底层架构冲突后，提交并推送到业务私有库
git push origin main
```

## 五、 各仓库远程源 (Git Remote) 快速校验

配置完成后，可在对应目录下运行 `git remote -v` 验证远程连接是否正确：

**`ck-boot-starter` 仓库配置：**

Plaintext

```
origin    https://your-domain.com/your-group/ck-boot-starter.git (fetch)
origin    https://your-domain.com/your-group/ck-boot-starter.git (push)
upstream  https://github.com/jeecgboot/JeecgBoot.git (fetch)
upstream  https://github.com/jeecgboot/JeecgBoot.git (push)
```

**`ck-shop` 业务仓库配置：**

Plaintext

```
base-template  https://your-domain.com/your-group/ck-boot-starter.git (fetch)
base-template  https://your-domain.com/your-group/ck-boot-starter.git (push)
origin         https://your-domain.com/your-group/ck-shop.git (fetch)
origin         https://your-domain.com/your-group/ck-shop.git (push)
```