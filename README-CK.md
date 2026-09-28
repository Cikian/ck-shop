# ck-boot-starter 通用脚手架

`ck-boot-starter` 是基于 **JeecgBoot** 官方单仓结构改造的工程化脚手架模板。

通过此脚手架，团队不仅能够沉淀自定义的底层通用组件（如 Logback 日志规范、OSS 文件存储、公共校验与权限工具），还能平滑吸收 JeecgBoot 官方的 Bug 修复与功能演化。

## 目录结构

Plaintext

```
ck-boot-starter/
├── admin/                  # 前端管理后台 (基于 原 jeecgboot-vue3 改造)
├── server/                 # 后端服务主工程 (基于 原 jeecg-boot 改造)
│   ├── jeecg-boot-base-core
│   ├── jeecg-boot-module
│   └── jeecg-module-system
└── README.md
```

## 快速开始：基于本脚手架派生新业务项目

当需要基于 `ck-boot-starter` 启动一个新的业务项目（例如 `ck-shop`）时，**请勿直接复制/粘贴本地文件夹**，请严格按照以下 Git 派生流程建立新项目，以保留后续的版本同步基因。

### 1. 在 Git 平台新建仓库

在你的私有 Git 平台（Gitee / GitLab / GitHub）上新建一个**空白的业务仓库**（如 `ck-shop.git`）。

### 2. 本地派生并绑定源

打开终端执行以下命令：

Bash

```
# 1. 克隆脚手架到新的业务项目目录
git clone <你的 ck-boot-starter 私有库地址.git> ck-shop

# 2. 进入新项目目录
cd ck-shop

# 3. 将原来的脚手架远程源标记为 base-template
git remote rename origin base-template

# 4. 绑定业务项目自己的私有仓库为 origin
git remote add origin <你的 ck-shop 私有库地址.git>

# 5. 将初始基线代码推送到新业务仓库
git push -u origin main
```

### 3. 开始业务开发

在 `ck-shop` 中直接进行业务代码开发。日常代码提交与推送将直接作用于业务仓库 `origin`：

Bash

```
git add .
git commit -m "feat: init shop module"
git push origin main
```

## 底座升级 SOP：如何同步最新代码？

### 场景 A：脚手架（`ck-boot-starter`）吸收 JeecgBoot 官方更新

当 JeecgBoot 官方发布新版本或安全补丁时，由脚手架维护者在 `ck-boot-starter` 项目中执行以下同步：

Bash

```
cd ck-boot-starter

# 1. 切到官方镜像分支并拉取最新官方代码
git checkout official-upstream
git pull upstream main

# 2. 切回脚手架开发主分支
git checkout main

# 3. 合并官方分支更新
git merge official-upstream -m "merge: sync latest official JeecgBoot updates"

# 4. 在 IDE 中解决冲突并提交推送
git push origin main
```

### 场景 B：业务项目（如 `ck-shop`）吸收脚手架更新

当脚手架 `ck-boot-starter` 更新了底层组件或同步了官方特性后，各业务项目可快速完成底层升级：

Bash

```
cd ck-shop

# 1. 拉取脚手架底座仓库的最新变更
git fetch base-template

# 2. 将脚手架最新的 main 分支合并到业务项目中
git merge base-template/main -m "merge: sync core architecture updates from ck-boot-starter"

# 3. 在 IDE 中完成代码冲突解决后，提交并推送到业务私有库
git push origin main
```

## 仓库源 (Git Remote) 配置参考

项目创建完成后，可在根目录下运行 `git remote -v` 检查配置是否正确：

- **`ck-boot-starter` 脚手架项目：**
  - `origin` $\rightarrow$ `ck-boot-starter.git` (读写)
  - `upstream` $\rightarrow$ `[https://github.com/jeecgboot/JeecgBoot.git](https://github.com/jeecgboot/JeecgBoot.git)` (只读)
- **`ck-shop` 等派生业务项目：**
  - `origin` $\rightarrow$ `ck-shop.git` (读写)
  - `base-template` $\rightarrow$ `ck-boot-starter.git` (只读)