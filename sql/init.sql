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
('家常菜', '日常家庭菜谱，下饭快手'),
('汤羹',   '各类汤品与羹汤，滋补养生'),
('甜品',   '甜点烘焙类，餐后小食'),
('川湘菜', '川湘风味，麻辣鲜香'),
('面点主食', '面条、米饭、饼类等主食'),
('凉菜',   '凉拌与冷盘，开胃解腻');

-- 种子配图文件记录（与 backend/uploads/ 下的 SVG 一一对应）。
-- 这些图片由 sql/gen-seed-images.mjs 生成，随仓库一起分发，
-- 因此 clone 后无需上传任何文件即可看到完整图文效果。
INSERT INTO `nx_system_file_info` (`id`, `originName`, `fileName`) VALUES
(1, '可乐鸡翅.svg', 'seed2-recipe-可乐鸡翅.svg'),
(2, '红烧肉.svg', 'seed2-recipe-红烧肉.svg'),
(3, '土豆炖牛肉.svg', 'seed2-recipe-土豆炖牛肉.svg'),
(4, '番茄炒蛋.svg', 'seed2-recipe-番茄炒蛋.svg'),
(5, '青椒炒肉丝.svg', 'seed2-recipe-青椒炒肉丝.svg'),
(6, '干煸豆角.svg', 'seed2-recipe-干煸豆角.svg'),
(7, '麻婆豆腐.svg', 'seed2-recipe-麻婆豆腐.svg'),
(8, '香菇滑鸡.svg', 'seed2-recipe-香菇滑鸡.svg'),
(9, '冬瓜排骨汤.svg', 'seed2-recipe-冬瓜排骨汤.svg'),
(10, '老鸭汤.svg', 'seed2-recipe-老鸭汤.svg'),
(11, '番茄鸡蛋汤.svg', 'seed2-recipe-番茄鸡蛋汤.svg'),
(12, '莲藕排骨汤.svg', 'seed2-recipe-莲藕排骨汤.svg'),
(13, '菌菇豆腐羹.svg', 'seed2-recipe-菌菇豆腐羹.svg'),
(14, '芒果糯米饭.svg', 'seed2-recipe-芒果糯米饭.svg'),
(15, '双皮奶.svg', 'seed2-recipe-双皮奶.svg'),
(16, '银耳莲子羹.svg', 'seed2-recipe-银耳莲子羹.svg'),
(17, '水煮肉片.svg', 'seed2-recipe-水煮肉片.svg'),
(18, '剁椒鱼头.svg', 'seed2-recipe-剁椒鱼头.svg'),
(19, '辣子鸡.svg', 'seed2-recipe-辣子鸡.svg'),
(20, '回锅肉.svg', 'seed2-recipe-回锅肉.svg'),
(21, '手擀面.svg', 'seed2-recipe-手擀面.svg'),
(22, '葱油饼.svg', 'seed2-recipe-葱油饼.svg'),
(23, '扬州炒饭.svg', 'seed2-recipe-扬州炒饭.svg'),
(24, '番茄牛腩面.svg', 'seed2-recipe-番茄牛腩面.svg'),
(25, '凉拌黄瓜.svg', 'seed2-recipe-凉拌黄瓜.svg'),
(26, '凉拌木耳.svg', 'seed2-recipe-凉拌木耳.svg'),
(27, '口水鸡.svg', 'seed2-recipe-口水鸡.svg'),
(28, '蒜泥白肉.svg', 'seed2-recipe-蒜泥白肉.svg'),
(29, '土豆.svg', 'seed2-material-土豆.svg'),
(30, '大虾.svg', 'seed2-material-大虾.svg'),
(31, '山药.svg', 'seed2-material-山药.svg'),
(32, '牛肉.svg', 'seed2-material-牛肉.svg'),
(33, '白菜.svg', 'seed2-material-白菜.svg'),
(34, '羊肉.svg', 'seed2-material-羊肉.svg'),
(35, '茄子.svg', 'seed2-material-茄子.svg'),
(36, '鱼.svg', 'seed2-material-鱼.svg'),
(37, '鲍鱼.svg', 'seed2-material-鲍鱼.svg'),
(38, '鸡翅.svg', 'seed2-material-鸡翅.svg'),
(39, '五花肉.svg', 'seed2-material-五花肉.svg'),
(40, '番茄.svg', 'seed2-material-番茄.svg'),
(41, '鸡蛋.svg', 'seed2-material-鸡蛋.svg'),
(42, '豆腐.svg', 'seed2-material-豆腐.svg'),
(43, '青椒.svg', 'seed2-material-青椒.svg'),
(44, '豆角.svg', 'seed2-material-豆角.svg'),
(45, '香菇.svg', 'seed2-material-香菇.svg'),
(46, '冬瓜.svg', 'seed2-material-冬瓜.svg'),
(47, '莲藕.svg', 'seed2-material-莲藕.svg'),
(48, '排骨.svg', 'seed2-material-排骨.svg'),
(49, '面粉.svg', 'seed2-material-面粉.svg'),
(50, '糯米.svg', 'seed2-material-糯米.svg'),
(51, '芒果.svg', 'seed2-material-芒果.svg'),
(52, '牛奶.svg', 'seed2-material-牛奶.svg'),
(53, '黄瓜.svg', 'seed2-material-黄瓜.svg'),
(54, '木耳.svg', 'seed2-material-木耳.svg'),
(55, '辣椒.svg', 'seed2-material-辣椒.svg'),
(56, '豆芽.svg', 'seed2-material-豆芽.svg');
-- 菜谱种子数据（共 28 道，覆盖 6 个分类）。
-- 每条数据的 fileName/fileId 均指向 nx_system_file_info 中真实存在的配图，
-- 图片位于 backend/uploads/，随仓库分发。
-- classifyId 对应上方 classify_info：1 家常菜 / 2 汤羹 / 3 甜品 / 4 川湘菜 / 5 面点主食 / 6 凉菜
INSERT INTO `foods_menu_info` (`name`, `description`, `tips`, `classifyId`, `fileName`, `fileId`, `userName`, `level`, `uploadUserId`) VALUES
-- ---------- 家常菜 ----------
('可乐鸡翅',   '鸡翅两面煎至金黄，倒入可乐没过食材，加生抽老抽小火焖煮十五分钟，最后大火收汁至浓稠挂浆。成品甜咸适中，色泽红亮。', '下饭菜',   1, 'seed2-recipe-可乐鸡翅.svg', 1, 'admin', 1, 1),
('红烧肉',     '五花肉切块冷水下锅焯去血沫，炒糖色至枣红后下肉块翻炒上色，加葱姜八角与热水没过，小火慢炖一小时至肥而不腻、入口即化。', '硬菜',     1, 'seed2-recipe-红烧肉.svg', 2, 'admin', 1, 1),
('土豆炖牛肉', '牛腩焯水后与葱姜同炒，加料酒与热水炖四十分钟，再下土豆块续炖二十分钟至软糯，汤汁浓郁，牛肉酥烂。', '炖菜',     1, 'seed2-recipe-土豆炖牛肉.svg', 3, 'admin', 1, 1),
('番茄炒蛋',   '鸡蛋打散炒至蓬松盛出，番茄去皮切块炒出沙，回锅鸡蛋加糖盐翻炒均匀，酸甜开胃，是零失败的家常快手菜。', '快手菜',   1, 'seed2-recipe-番茄炒蛋.svg', 4, 'admin', 1, 1),
('青椒炒肉丝', '里脊切丝加淀粉生抽抓匀腌制，热油滑散盛出，爆香蒜末后下青椒丝炒断生，回锅肉丝调味翻匀即可。', '快手下饭', 1, 'seed2-recipe-青椒炒肉丝.svg', 5, 'admin', 1, 1),
('干煸豆角',   '豆角掐段撕去老筋，油锅炸至表皮起皱盛出，爆香花椒干辣椒后回锅煸炒，加蒜末与盐调味，干香入味。', '素菜',     1, 'seed2-recipe-干煸豆角.svg', 6, 'admin', 1, 1),
('麻婆豆腐',   '嫩豆腐切块焯水去豆腥，炒香豆瓣酱与肉末，加高汤下豆腐轻推煮三分钟，水淀粉勾芡后撒花椒粉与葱花。', '麻辣鲜香', 1, 'seed2-recipe-麻婆豆腐.svg', 7, 'admin', 1, 1),
('香菇滑鸡',   '鸡腿肉切块用生抽淀粉腌制，与泡发的香菇同炒后加少许水焖煮，收汁后鸡肉滑嫩、菌香浓郁。', '家常蒸炒', 1, 'seed2-recipe-香菇滑鸡.svg', 8, 'admin', 1, 1),
('冬瓜排骨汤', '排骨焯水后与姜片同炖四十分钟，加入冬瓜块续炖十五分钟，起锅前加盐，汤清味鲜，清热解腻。', '养生汤',   1, 'seed2-recipe-冬瓜排骨汤.svg', 9, 'admin', 1, 1),
-- ---------- 汤羹 ----------
('老鸭汤',     '老鸭斩块焯水，与姜片、山药一同入砂锅加水没过，小火慢炖两小时，汤清味鲜，滋阴润燥，适合秋冬滋补。', '养生汤',   2, 'seed2-recipe-老鸭汤.svg', 10, 'admin', 1, 1),
('番茄鸡蛋汤', '番茄炒出沙后加开水煮沸，淋入蛋液形成蛋花，加盐与香油调味，撒葱花出锅，酸鲜清爽。', '快手汤',   2, 'seed2-recipe-番茄鸡蛋汤.svg', 11, 'admin', 1, 1),
('莲藕排骨汤', '排骨焯水与姜片同煲一小时，加莲藕块续煲四十分钟，藕粉糯拉丝，汤味甘甜。', '滋补汤',   2, 'seed2-recipe-莲藕排骨汤.svg', 12, 'admin', 1, 1),
('菌菇豆腐羹', '香菇木耳切丝与豆腐丁同煮，加高汤调味后用水淀粉勾薄芡，淋蛋清搅出蛋花，撒白胡椒粉提香。', '清淡羹汤', 2, 'seed2-recipe-菌菇豆腐羹.svg', 13, 'admin', 1, 1),
-- ---------- 甜品 ----------
('芒果糯米饭', '糯米提前浸泡四小时蒸熟，拌入椰浆与糖静置入味，配切块芒果同食，椰香浓郁、甜糯可口。', '泰式甜品', 3, 'seed2-recipe-芒果糯米饭.svg', 14, 'admin', 1, 1),
('双皮奶',     '牛奶加热结出奶皮，蛋清加糖打散后与牛奶混合过筛，回碗盖膜蒸十二分钟，冷藏后口感细嫩如布丁。', '广式甜品', 3, 'seed2-recipe-双皮奶.svg', 15, 'admin', 1, 1),
('银耳莲子羹', '银耳泡发撕小朵，与莲子同煮一小时至出胶，加冰糖与枸杞再煮十分钟，胶质浓稠、润燥养颜。', '养颜甜羹', 3, 'seed2-recipe-银耳莲子羹.svg', 16, 'admin', 1, 1),
-- ---------- 川湘菜 ----------
('水煮肉片',   '里脊切薄片用蛋清淀粉上浆，豆瓣酱炒出红油加高汤煮开，下配菜垫底、肉片汆熟，撒辣椒面与花椒，泼热油激香。', '麻辣过瘾', 4, 'seed2-recipe-水煮肉片.svg', 17, 'admin', 1, 1),
('剁椒鱼头',   '鱼头对半剖开铺盘，覆盖剁椒与蒜末姜丝，大火蒸十分钟，出锅淋热油与蒸鱼豉油，鲜辣开胃。', '湘菜代表', 4, 'seed2-recipe-剁椒鱼头.svg', 18, 'admin', 1, 1),
('辣子鸡',     '鸡腿肉切丁腌制后炸至外酥，锅中留底油爆香大量干辣椒与花椒，回锅鸡丁翻炒均匀，麻辣干香。', '川味经典', 4, 'seed2-recipe-辣子鸡.svg', 19, 'admin', 1, 1),
('回锅肉',     '五花肉整块煮至八分熟切薄片，下锅煸出灯盏窝，加豆瓣酱与甜面酱炒香，配青蒜段翻炒出锅。', '川菜之首', 4, 'seed2-recipe-回锅肉.svg', 20, 'admin', 1, 1),
-- ---------- 面点主食 ----------
('手擀面',     '面粉加水与少许盐揉成硬面团，醒面三十分钟后擀薄切条，沸水煮至浮起，配任意浇头或做汤面皆宜。', '主食',     5, 'seed2-recipe-手擀面.svg', 21, 'admin', 1, 1),
('葱油饼',     '面团擀薄抹油撒盐与葱花，卷起再擀成饼，平底锅小火烙至两面金黄，层次分明、外酥内软。', '早餐主食', 5, 'seed2-recipe-葱油饼.svg', 22, 'admin', 1, 1),
('扬州炒饭',   '隔夜米饭打散，鸡蛋炒散后下虾仁、火腿丁与青豆翻炒，加米饭同炒至粒粒分明，调味出锅。', '经典炒饭', 5, 'seed2-recipe-扬州炒饭.svg', 23, 'admin', 1, 1),
('番茄牛腩面', '牛腩炖至酥烂，番茄炒沙后与牛肉同煮成浓汤，浇在煮好的面条上，汤浓面滑，酸香开胃。', '汤面',     5, 'seed2-recipe-番茄牛腩面.svg', 24, 'admin', 1, 1),
-- ---------- 凉菜 ----------
('凉拌黄瓜',   '黄瓜拍裂切段，加蒜末、香醋、生抽、香油与少许糖拌匀，冷藏十分钟更爽脆，是最快手的开胃凉菜。', '开胃小菜', 6, 'seed2-recipe-凉拌黄瓜.svg', 25, 'admin', 1, 1),
('凉拌木耳',   '木耳泡发后焯水过凉，加蒜末、小米辣、香醋与生抽拌匀，爽脆弹牙，低脂解腻。', '爽口凉拌', 6, 'seed2-recipe-凉拌木耳.svg', 26, 'admin', 1, 1),
('口水鸡',     '鸡腿煮熟后冰水浸泡收紧肉质，斩件摆盘，淋上由红油、花椒粉、蒜末、生抽与香醋调成的料汁，撒花生碎与葱花。', '川式凉菜', 6, 'seed2-recipe-口水鸡.svg', 27, 'admin', 1, 1),
('蒜泥白肉',   '五花肉整块煮至断生切薄片摆盘，淋蒜泥红油料汁，肥而不腻，蒜香浓郁。', '经典凉盘', 6, 'seed2-recipe-蒜泥白肉.svg', 28, 'admin', 1, 1);

-- 食材种子数据（共 28 种），fileId 指向真实配图。
INSERT INTO `foods_material_info` (`name`, `description`, `fileName`, `fileId`, `userName`, `level`, `uploadUserId`) VALUES
('土豆',   '常见根茎类蔬菜，可炒可炖，淀粉含量高，适合做土豆丝、土豆炖牛肉。', 'seed2-material-土豆.svg', 29, 'admin', 1, 1),
('大虾',   '新鲜海虾，肉质紧实，适合白灼、油焖或蒜蓉开背。', 'seed2-material-大虾.svg', 30, 'admin', 1, 1),
('山药',   '健脾养胃的根茎食材，口感绵软，可煲汤也可清炒。', 'seed2-material-山药.svg', 31, 'admin', 1, 1),
('牛肉',   '优质蛋白来源，适合炖煮、快炒或做汤，牛腩部位最适合慢炖。', 'seed2-material-牛肉.svg', 32, 'admin', 1, 1),
('白菜',   '大众蔬菜，清甜爽脆，百搭食材，适合炖、炒、做馅。', 'seed2-material-白菜.svg', 33, 'admin', 1, 1),
('羊肉',   '温补食材，适合冬季火锅、红烧或炖汤，可去膻后烹饪。', 'seed2-material-羊肉.svg', 34, 'admin', 1, 1),
('茄子',   '紫色蔬菜，吸味性强，适合鱼香、红烧或蒜蓉蒸。', 'seed2-material-茄子.svg', 35, 'admin', 1, 1),
('鱼',     '富含优质蛋白和不饱和脂肪酸，适合清蒸、红烧或煮汤。', 'seed2-material-鱼.svg', 36, 'admin', 1, 1),
('鲍鱼',   '高档海鲜食材，口感弹嫩，鲜味十足，适合清蒸或红烧。', 'seed2-material-鲍鱼.svg', 37, 'admin', 1, 1),
('鸡翅',   '肉质细嫩的禽类食材，适合可乐烧、奥尔良烤或卤制。', 'seed2-material-鸡翅.svg', 38, 'admin', 1, 1),
('五花肉', '肥瘦相间的猪肉，是红烧肉、回锅肉的核心食材。', 'seed2-material-五花肉.svg', 39, 'admin', 1, 1),
('番茄',   '酸甜多汁的茄果类蔬菜，适合炒蛋、煮汤或做酱汁。', 'seed2-material-番茄.svg', 40, 'admin', 1, 1),
('鸡蛋',   '优质蛋白食材，可炒、蒸、煮、煎，家常必备。', 'seed2-material-鸡蛋.svg', 41, 'admin', 1, 1),
('豆腐',   '大豆制品，口感嫩滑，适合麻婆、红烧或做汤。', 'seed2-material-豆腐.svg', 42, 'admin', 1, 1),
('青椒',   '微辣清脆的蔬菜，常用于炒肉丝、虎皮青椒等。', 'seed2-material-青椒.svg', 43, 'admin', 1, 1),
('豆角',   '长荚类蔬菜，需充分加热后食用，适合干煸或炖肉。', 'seed2-material-豆角.svg', 44, 'admin', 1, 1),
('香菇',   '鲜味浓郁的菌类，适合炖汤、炒菜或做素高汤。', 'seed2-material-香菇.svg', 45, 'admin', 1, 1),
('冬瓜',   '清淡利水的瓜类，适合煮汤，夏季消暑佳品。', 'seed2-material-冬瓜.svg', 46, 'admin', 1, 1),
('莲藕',   '根茎类食材，口感脆嫩，适合凉拌或煲汤。', 'seed2-material-莲藕.svg', 47, 'admin', 1, 1),
('排骨',   '猪肋排，适合红烧、糖醋或煲汤。', 'seed2-material-排骨.svg', 48, 'admin', 1, 1),
('面粉',   '小麦粉，制作面点与主食的基础原料。', 'seed2-material-面粉.svg', 49, 'admin', 1, 1),
('糯米',   '黏性米，适合做粽子、糯米饭与甜品。', 'seed2-material-糯米.svg', 50, 'admin', 1, 1),
('芒果',   '热带水果，香甜多汁，适合做甜品与冰沙。', 'seed2-material-芒果.svg', 51, 'admin', 1, 1),
('牛奶',   '乳制品，可用于烘焙、煮羹与饮品。', 'seed2-material-牛奶.svg', 52, 'admin', 1, 1),
('黄瓜',   '清脆爽口的瓜类，最常见的凉拌食材。', 'seed2-material-黄瓜.svg', 53, 'admin', 1, 1),
('木耳',   '菌类干货，泡发后脆爽，适合凉拌或炒制。', 'seed2-material-木耳.svg', 54, 'admin', 1, 1),
('辣椒',   '辛辣调味蔬菜，川湘菜的灵魂食材。', 'seed2-material-辣椒.svg', 55, 'admin', 1, 1),
('豆芽',   '芽苗类蔬菜，清脆易熟，适合快炒或做汤。', 'seed2-material-豆芽.svg', 56, 'admin', 1, 1);
