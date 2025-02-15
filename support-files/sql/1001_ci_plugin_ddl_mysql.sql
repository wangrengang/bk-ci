USE devops_ci_plugin;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for T_PLUGIN_CODECC
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PLUGIN_CODECC` (
  `ID` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `PROJECT_ID` varchar(64) DEFAULT NULL COMMENT '项目ID',
  `PIPELINE_ID` varchar(34) DEFAULT NULL COMMENT '流水线ID',
  `BUILD_ID` varchar(34) DEFAULT NULL COMMENT '构建ID',
  `TASK_ID` varchar(34) DEFAULT NULL COMMENT '任务ID',
  `TOOL_SNAPSHOT_LIST` longtext COMMENT '',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `BUILD_ID_KEY` (`BUILD_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='';

-- ----------------------------
-- Table structure for T_PLUGIN_CODECC_ELEMENT
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PLUGIN_CODECC_ELEMENT` (
  `ID` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `PROJECT_ID` varchar(128) DEFAULT NULL COMMENT '项目ID',
  `PIPELINE_ID` varchar(34) DEFAULT NULL COMMENT '流水线ID',
  `TASK_NAME` varchar(256) DEFAULT NULL COMMENT '任务名称',
  `TASK_CN_NAME` varchar(256) DEFAULT NULL COMMENT '任务中文名称',
  `TASK_ID` varchar(128) DEFAULT NULL COMMENT '任务ID',
  `IS_SYNC` varchar(6) DEFAULT NULL COMMENT '是否是同步',
  `SCAN_TYPE` varchar(6) DEFAULT NULL COMMENT '扫描类型（0：全量, 1：增量）',
  `LANGUAGE` varchar(1024) DEFAULT NULL COMMENT '工程语言',
  `PLATFORM` varchar(16) DEFAULT NULL COMMENT 'codecc原子执行环境，例如WINDOWS，LINUX，MACOS等',
  `TOOLS` varchar(1024) DEFAULT NULL COMMENT '扫描工具',
  `PY_VERSION` varchar(16) DEFAULT NULL COMMENT '其中“py2”表示使用python2版本，“py3”表示使用python3版本',
  `ESLINT_RC` varchar(16) DEFAULT NULL COMMENT 'js项目框架',
  `CODE_PATH` longtext COMMENT '代码存放路径',
  `SCRIPT_TYPE` varchar(16) DEFAULT NULL COMMENT '脚本类型',
  `SCRIPT` longtext COMMENT '打包脚本',
  `CHANNEL_CODE` varchar(16) DEFAULT NULL COMMENT '渠道号，默认为DS',
  `UPDATE_USER_ID` varchar(128) DEFAULT NULL COMMENT '更新的用户id',
  `IS_DELETE` varchar(6) DEFAULT NULL COMMENT '是否删除 0 可用 1删除',
  `UPDATE_TIME` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `PROJECT_PIPELINE_INDEX` (`PROJECT_ID`,`PIPELINE_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='';

-- ----------------------------
-- Table structure for T_PLUGIN_GITHUB_CHECK
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PLUGIN_GITHUB_CHECK` (
  `ID` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `PIPELINE_ID` varchar(64) NOT NULL COMMENT '流水线ID',
  `BUILD_NUMBER` int(11) NOT NULL COMMENT '构建编号',
  `REPO_ID` varchar(64) DEFAULT NULL COMMENT '代码库ID',
  `COMMIT_ID` varchar(64) NOT NULL COMMENT '代码提交ID',
  `CHECK_RUN_ID` bigint(20) NOT NULL COMMENT '',
  `CREATE_TIME` datetime NOT NULL COMMENT '创建时间',
  `UPDATE_TIME` datetime NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `REPO_NAME` varchar(128) DEFAULT NULL COMMENT '代码库别名',
  `CHECK_RUN_NAME` VARCHAR(64) NULL DEFAULT NULL COMMENT '',
  PRIMARY KEY (`ID`),
  KEY `PIPELINE_ID_REPO_ID_COMMIT_ID` (`PIPELINE_ID`,`COMMIT_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='';

-- ----------------------------
-- Table structure for T_PLUGIN_GIT_CHECK
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PLUGIN_GIT_CHECK` (
  `ID` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `PIPELINE_ID` varchar(64) NOT NULL COMMENT '流水线ID',
  `BUILD_NUMBER` int(11) NOT NULL COMMENT '构建编号',
  `REPO_ID` varchar(64) DEFAULT NULL COMMENT '代码库ID',
  `COMMIT_ID` varchar(64) NOT NULL COMMENT '代码提交ID',
  `CREATE_TIME` datetime NOT NULL COMMENT '创建时间',
  `UPDATE_TIME` datetime NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `REPO_NAME` varchar(128) DEFAULT NULL COMMENT '代码库别名',
  `CONTEXT` VARCHAR(255) DEFAULT NULL COMMENT '内容',
  PRIMARY KEY (`ID`),
  KEY `PIPELINE_ID_REPO_ID_COMMIT_ID` (`PIPELINE_ID`,`COMMIT_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='';

SET FOREIGN_KEY_CHECKS = 1;
