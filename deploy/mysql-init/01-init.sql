-- 只在数据卷第一次创建时执行；表结构由应用启动时的 Liquibase 负责
CREATE DATABASE IF NOT EXISTS roam_bupt
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
