CREATE DATABASE IF NOT EXISTS `delicious_foods` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `delicious_foods`;

DROP TABLE IF EXISTS `praise_info`;
DROP TABLE IF EXISTS `collect_info`;
DROP TABLE IF EXISTS `foods_menu_info`;
DROP TABLE IF EXISTS `foods_material_info`;
DROP TABLE IF EXISTS `classify_info`;
DROP TABLE IF EXISTS `nx_system_file_info`;
DROP TABLE IF EXISTS `user_info`;
DROP TABLE IF EXISTS `admin_info`;

CREATE TABLE `admin_info` (
  `id`       BIGINT       NOT NULL AUTO_INCREMENT,
  `name`     VARCHAR(64)  DEFAULT NULL,
  `password` VARCHAR(64)  DEFAULT NULL,
  `nickName` VARCHAR(64)  DEFAULT NULL,
  `sex`      VARCHAR(8)   DEFAULT NULL,
  `age`      INT          DEFAULT NULL,
  `birthday` VARCHAR(32)  DEFAULT NULL,
  `phone`    VARCHAR(32)  DEFAULT NULL,
  `address`  VARCHAR(255) DEFAULT NULL,
  `email`    VARCHAR(64)  DEFAULT NULL,
  `cardId`   VARCHAR(32)  DEFAULT NULL,
  `level`    INT          DEFAULT 1,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `user_info` (
  `id`       BIGINT       NOT NULL AUTO_INCREMENT,
  `name`     VARCHAR(64)  DEFAULT NULL,
  `password` VARCHAR(64)  DEFAULT NULL,
  `nickName` VARCHAR(64)  DEFAULT NULL,
  `sex`      VARCHAR(8)   DEFAULT NULL,
  `age`      INT          DEFAULT NULL,
  `birthday` VARCHAR(32)  DEFAULT NULL,
  `phone`    VARCHAR(32)  DEFAULT NULL,
  `address`  VARCHAR(255) DEFAULT NULL,
  `email`    VARCHAR(64)  DEFAULT NULL,
  `cardId`   VARCHAR(32)  DEFAULT NULL,
  `level`    INT          DEFAULT 2,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `nx_system_file_info` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `originName` VARCHAR(255) DEFAULT NULL,
  `fileName`   VARCHAR(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `classify_info` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `name`        VARCHAR(64)  DEFAULT NULL,
  `description` VARCHAR(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `foods_material_info` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `name`         VARCHAR(128) DEFAULT NULL,
  `description`  TEXT         DEFAULT NULL,
  `fileName`     VARCHAR(255) DEFAULT NULL,
  `fileId`       BIGINT       DEFAULT NULL,
  `userName`     VARCHAR(64)  DEFAULT NULL,
  `level`        INT          DEFAULT NULL,
  `uploadUserId` BIGINT       DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `foods_menu_info` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `name`         VARCHAR(128) DEFAULT NULL,
  `description`  TEXT         DEFAULT NULL,
  `tips`         VARCHAR(255) DEFAULT NULL,
  `classifyId`   BIGINT       DEFAULT NULL,
  `fileName`     VARCHAR(255) DEFAULT NULL,
  `fileId`       BIGINT       DEFAULT NULL,
  `userName`     VARCHAR(64)  DEFAULT NULL,
  `level`        INT          DEFAULT NULL,
  `uploadUserId` BIGINT       DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `collect_info` (
  `id`      BIGINT       NOT NULL AUTO_INCREMENT,
  `name`    VARCHAR(128) DEFAULT NULL,
  `time`    VARCHAR(32)  DEFAULT NULL,
  `foodsId` BIGINT       DEFAULT NULL,
  `userId`  BIGINT       DEFAULT NULL,
  `level`   INT          DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `praise_info` (
  `id`      BIGINT       NOT NULL AUTO_INCREMENT,
  `name`    VARCHAR(128) DEFAULT NULL,
  `time`    VARCHAR(32)  DEFAULT NULL,
  `foodsId` BIGINT       DEFAULT NULL,
  `userId`  BIGINT       DEFAULT NULL,
  `level`   INT          DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `admin_info` (`name`, `password`, `nickName`, `sex`, `level`) VALUES
('admin', '123456', '系统管理员', '男', 1);

INSERT INTO `user_info` (`name`, `password`, `nickName`, `sex`, `level`) VALUES
('user', '123456', '测试用户', '女', 2);

INSERT INTO `classify_info` (`name`, `description`) VALUES
('家常菜', '日常家庭菜谱'),
('甜品', '甜点烘焙类');

INSERT INTO `nx_system_file_info` (`id`, `originName`, `fileName`) VALUES
(1,  '什锦.jpeg',    '什锦1601474433677.jpeg'),
(2,  '可乐鸡翅.jpg', '可乐鸡翅1601438117374.jpg'),
(3,  '土豆.jpg',     '土豆1601459240858.jpg'),
(4,  '大虾.jpeg',    '大虾1601439149080.jpeg'),
(5,  '山药.jpg',     '山药1601459251759.jpg'),
(6,  '牛肉.jpg',     '牛肉1601459286317.jpg'),
(7,  '白菜.jpg',     '白菜1601459261990.jpg'),
(8,  '红烧肉.jpeg',  '红烧肉1601439136536.jpeg'),
(9,  '羊肉.jpg',     '羊肉1601459272290.jpg'),
(10, '老鸭汤.jpg',   '老鸭汤1601473385688.jpg'),
(11, '茄子.jpg',     '茄子1601459321029.jpg'),
(12, '鱼.jpg',       '鱼1601459303467.jpg'),
(13, '鲍鱼.jpg',     '鲍鱼1601459358338.jpg');

INSERT INTO `foods_menu_info` (`name`, `description`, `tips`, `classifyId`, `fileName`, `fileId`, `userName`, `level`, `uploadUserId`) VALUES
('可乐鸡翅', '经典家常菜，甜咸适中，色泽红亮，老少皆宜。', '下饭菜', 1, '可乐鸡翅1601438117374.jpg', 2,  'admin', 1, 1),
('红烧肉',   '肥而不腻、入口即化的传统名菜，酱香浓郁。',   '硬菜',   1, '红烧肉1601439136536.jpeg',  8,  'admin', 1, 1),
('老鸭汤',   '汤清味鲜，滋阴润燥，适合秋冬滋补。',         '养生汤', 1, '老鸭汤1601473385688.jpg',   10, 'admin', 1, 1),
('什锦',     '多种食材搭配，色彩丰富，营养均衡。',          '快手菜', 1, '什锦1601474433677.jpeg',    1,  'admin', 1, 1);

INSERT INTO `foods_material_info` (`name`, `description`, `fileName`, `fileId`, `userName`, `level`, `uploadUserId`) VALUES
('土豆', '常见根茎类蔬菜，可炒可炖，淀粉含量高。',   '土豆1601459240858.jpg',  3,  'admin', 1, 1),
('大虾', '新鲜海虾，肉质紧实，适合白灼或油焖。',     '大虾1601439149080.jpeg', 4,  'admin', 1, 1),
('山药', '健脾养胃的根茎食材，口感绵软。',           '山药1601459251759.jpg',  5,  'admin', 1, 1),
('牛肉', '优质蛋白来源，适合炖煮或快炒。',           '牛肉1601459286317.jpg',  6,  'admin', 1, 1),
('白菜', '大众蔬菜，清甜爽脆，百搭食材。',           '白菜1601459261990.jpg',  7,  'admin', 1, 1),
('羊肉', '温补食材，适合冬季火锅或红烧。',           '羊肉1601459272290.jpg',  9,  'admin', 1, 1),
('茄子', '紫色蔬菜，吸味性强，适合鱼香或红烧。',     '茄子1601459321029.jpg',  11, 'admin', 1, 1),
('鱼',   '新鲜鱼类，富含优质蛋白和不饱和脂肪酸。',   '鱼1601459303467.jpg',    12, 'admin', 1, 1),
('鲍鱼', '高档海鲜食材，口感弹嫩，鲜味十足。',       '鲍鱼1601459358338.jpg',  13, 'admin', 1, 1);
