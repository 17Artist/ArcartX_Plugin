/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.language

import priv.seventeen.artist.arcartx.commons.language.ArcartXLanguageManager
import priv.seventeen.artist.arcartx.commons.language.LanguageKey
import priv.seventeen.artist.blink.bukkitPlugin

/** 内置语言键枚举，定义插件各模块的本地化消息模板 */
enum class AXLanguageKey(var message: String): LanguageKey {

    // ========== 启动/核心 ==========
    WELCOME_MESSAGE("欢迎使用 {0}"),
    PROJECT_URL("项目地址: &9https://arcartx.com"),
    SERVER_VERSION("当前服务端版本：{0}"),
    REGISTER_CHANNEL("注册信道: &a{0}"),

    // ========== 配置加载 ==========
    CLIENT_TITLE("客户端默认标题: &b{0}"),
    DATABASE_LOADED("数据库配置加载完成"),
    LOAD_ENCRYPTED_RESOURCE("加载&b{0}&f个加密资源包"),
    LOAD_PAPI_BLACKLIST("加载&b{0}&f个占位符API黑名单"),
    LOAD_SCRIPT_IMPORTS("加载&b{0}&f个脚本外部类导入"),
    LOAD_BOSSBAR("加载&b{0}&f个BossBar"),
    LOAD_CHAT_CARD("加载&b{0}&f个聊天卡片模板"),
    LOAD_DAMAGE_DISPLAY("加载&b{0}&f个伤害显示配置"),
    LOAD_FONT_ICON("加载&b{0}&f个字体图标"),
    LOAD_SCENE_CAMERA("加载&b{0}&f个场景相机配置"),
    LOAD_WAYPOINT("加载&b{0}&f个路标配置"),
    LOAD_UI("加载&b{0}&f个UI"),
    LOAD_TIP("加载&b{0}&f个TIP"),
    LOAD_SLOT("加载&b{0}&f个额外槽位"),
    LOAD_ENTITY_MODEL("加载&b{0}&f个实体模型配置"),
    LOAD_MODEL_LINK("加载&b{0}&f个模型链接配置"),
    LOAD_SIMPLE_KEY("加载&b{0}&f个简单按键"),
    LOAD_KEY_GROUP("加载&b{0}&f个按键组合"),
    LOAD_CLIENT_KEY("加载&b{0}&f个客户端按键"),
    LOAD_ITEM_EFFECT("加载&b{0}&f个物品额外显示配置"),
    LOAD_WORLD_HOLOGRAM("加载&b{0}&f个世界全息配置"),
    LOAD_ENTITY_HOLOGRAM("加载&b{0}&f个实体血条全息配置"),
    LOAD_HOLOGRAM("加载&b{0}&f个全息配置"),
    LOAD_CAMERA_PRESET("加载&b{0}&f个相机预设配置"),
    LOAD_AREA("加载&b{0}&f个区域"),
    LOAD_FUNCTION_SCRIPT("加载&b{0}&f个方法脚本"),
    LOAD_EVENT_SCRIPT("加载&b{0}&f个事件脚本"),

    // ========== 插件检测 ==========
    FOUND_ASTRAXHERO("&a发现AstraXHero插件，注册属性提供器"),
    FOUND_ATTRIBUTE_PLUS_OLD("&a发现AttributePlus插件(旧版)，注册属性提供器"),
    FOUND_ATTRIBUTE_PLUS("&a发现AttributePlus插件，注册属性提供器"),
    FOUND_CRANE_ATTRIBUTE("&a发现CraneAttribute插件，注册属性提供器"),
    FOUND_VAULT("&a发现Vault插件，注册经济提供器"),
    FOUND_PLAYER_POINTS("&a发现PlayerPoints插件，注册经济提供器"),
    FOUND_RONDO("&a发现Rondo插件，注册{0}个货币经济提供器"),
    FOUND_NEIGE_ITEMS("&a发现NeigeItems插件，注册物品提供器"),
    FOUND_MYTHICMOBS_ITEM("&a发现MythicMobs插件，注册物品提供器"),
    FOUND_OVERTURE("&a发现Overture插件，注册物品提供器与{0}项物品Meta"),
    OVERTURE_INTEGRATION_FAILED("&cOverture兼容扩展注册失败: {0}"),
    FOUND_MYTHICMOBS("&a发现MythicMobs插件，注册相关功能"),
    FOUND_MODEL_ENGINE("&a发现ModelEngine插件，碰撞体积处理器-兼容模式"),
    FOUND_ADYESHACH("&a发现Adyeshach插件，加载模型以及虚拟碰撞体积"),
    REGISTER_MODEL_BULLET_FAILED("尝试注册模型子弹类型反射失败"),

    // ========== 命令系统 ==========
    COMMAND_NOT_FOUND("命令不存在: &c/{0} {1}"),
    COMMAND_LIST("指令列表"),
    COMMAND_LIST_ITEM(" - &b/{0} {1} &f- {2}"),
    SUB_COMMAND_LIST("子命令列表"),
    SUB_COMMAND_LIST_ITEM("- &b/{0} {1} {2} {3} &f- {4}"),
    ONLY_OP("该指令只能由OP执行"),
    ONLY_CONSOLE("该指令只能由控制台执行"),
    ONLY_PLAYER("该指令只能由玩家执行"),
    NO_PERMISSION("权限不足，需要权限 &c{0}"),
    ARGS_MISMATCH("参数长度不匹配，需要:&c{0}"),
    COMMAND_ERROR("执行指令时出现错误"),

    // ========== 命令参数 ==========
    ARG_NOT_INT("您输入的参数：{0}不是一个整数"),
    ARG_NOT_DOUBLE("您输入的参数：{0}不是一个小数"),
    ARG_NOT_LONG("您输入的参数：{0}不是一个长整数"),
    ARG_NOT_FLOAT("您输入的参数：{0}不是一个小数"),
    ARG_NOT_BOOLEAN("您输入的参数：{0}必须是 true 或 false"),
    PLAYER_NOT_ONLINE("玩家：{0}不在线"),
    ARG_NOT_UUID("您输入的参数：{0}不是一个UUID"),

    // ========== 指令功能 ==========
    SLOT_DEBUG_MODE("槽位调试模式:{0}"),
    REQUEST_IN_PROGRESS("正在发起请求，请稍候..."),
    RESOURCE_SYNC_COMPLETE("文件校验信息同步完成，共更新 {0} 条记录。"),
    RESOURCE_SYNC_FAILED("文件校验信息同步失败: {0}"),
    RESOURCE_CLIENT_NOT_ENABLED("资源客户端未开启，无法同步文件校验信息。"),
    RELOADING("重载中..."),
    RELOAD_COMPLETE("重载完成"),
    HOLD_ITEM_REQUIRED("&c请手持物品"),
    CRC_UPDATE_SUCCESS("&a更新成功"),
    CRC_UPDATE_FAILED("&c更新失败"),
    CAMERA_EDIT_ENTER("已进入绝对坐标相机编辑模式"),
    CAMERA_EDIT_INSTRUCTION("移动到所需位置确定坐标点后按下&bALT&f保存坐标步，聊天框输入&cend + 名称&f结束编辑"),
    CAMERA_EDIT_EXAMPLE("示例: &eend scene1"),
    CAMERA_EDIT_EXIT("输入错误 已退出相机编辑模式"),

    // ========== 网络/CRC ==========
    CRC_MISMATCH("您的拓展CRC64列表和服务器不匹配(&a注: 该消息仅OP可见&f)"),
    CRC_LIST_ITEM("&7 - &f{0}"),
    CRC_UPDATE_COMMAND("§a指令: &e/a crc64 update &a将客户端CRC64值同步至服务端"),
    SEND_RESOURCE_UPDATE("发送玩家资源更新: 操作文件数: {0}"),

    // ========== 相机编辑器 ==========
    NO_COORDINATES_RECORDED("未记录任何坐标"),
    CAMERA_CREATE_SUCCESS("创建成功,重载后生效"),


    // ========== 资源服务 ==========
    RESOURCE_SERVICE_START("启动资源文件服务"),
    RESOURCE_FILE_SYNCED("&b已同步资源文件信息，共 {0} 个文件"),
    RESOURCE_FILE_SYNC_FAILED("§c资源文件信息同步失败: {0}"),


    // ========== 区域编辑器 ==========
    AREA_LEFT_CLICK_SELECTED("已选择左键点"),
    AREA_SELECTION_CLEARED("已清除选择"),
    AREA_RIGHT_CLICK_SELECTED("已选择右键点"),
    AREA_NOT_FOUND("未找到区域"),
    AREA_PRIORITY_PROMPT("已选择区域&b{0}&f请输入优先级(数字越大优先级越高)"),
    AREA_PRIORITY_SET("已设置优先级为 {0}"),
    AREA_INPUT_NUMBER("请输入正确的数字"),
    AREA_DELETED("已删除区域&b{0}&f"),
    AREA_SELECT_LEFT_FIRST("请先选择左键点"),
    AREA_SELECT_RIGHT_FIRST("请先选择右键点"),
    AREA_INPUT_NAME("请输入区域名称"),
    AREA_INPUT_VALID_NAME("请输入正确的名称"),

    // ========== 区域管理 ==========
    AREA_DIFFERENT_WORLD("两个位置不在同一个世界"),
    AREA_ALREADY_EXISTS("已存在名为&b{0}&f的区域"),
    AREA_ADD_SUCCESS("添加区域&b{0}&f成功"),

    // ========== 数据库 ==========
    DB_SLOT_CREATED_MYSQL("创建额外槽位数据库完成(&dMYSQL&f)"),
    DB_SLOT_CLOSED_MYSQL("关闭额外槽位数据数据库连接"),
    DB_SLOT_CREATED_SQLITE("创建额外槽位数据库完成(&dSQLITE&f)"),
    DB_SLOT_CLOSED_SQLITE("关闭额外槽位数据库连接"),

    // ========== 异常/错误来源 ==========
    EXCEPTION_COMMAND_INVOKE("指令 /{0}调用"),
    EXCEPTION_COMMAND_REGISTER("命令注册"),
    EXCEPTION_DB_INIT_FAILED("初始化数据库失败"),
    EXCEPTION_DB_LOGIN_TIMEOUT_FAILED("设置数据库登录超时时间失败"),
    EXCEPTION_CREATE_SLOT_DB_FAILED("创建额外槽位数据库失败"),
    EXCEPTION_QUERY_SLOT_DATA_FAILED("查询槽位数据失败"),
    EXCEPTION_LOAD_SLOT_DATA_FAILED("加载槽位数据失败"),
    EXCEPTION_SET_SLOT_DATA_FAILED("设置槽位数据失败"),
    EXCEPTION_DB_CLOSE_FAILED("关闭数据库连接失败"),
    EXCEPTION_PACKET_HANDLE("处理发包发生异常"),
    EXCEPTION_LANGUAGE_FILE_WRITE("Language file write"),
    EXCEPTION_CONFIG_WRITE("{0} 写出出错"),
    EXCEPTION_CONFIG_READ("{0} 读取文件出错"),
    EXCEPTION_CONFIG_FIELD_LOAD("{0}:&6{1}.{2}"),
    EXCEPTION_CONFIG_FIELD_WRITE("{0}.{1}"),
    EXCEPTION_ITEM_WRITE("写入物品错误"),
    EXCEPTION_MAP_KEY_NOT_STRING("Map字段的键类型必须为String"),
    EXCEPTION_MAP_TWO_GENERICS("Map字段必须有两个泛型参数"),
    EXCEPTION_MAP_PARAMETERIZED("Map字段必须为参数化类型"),
    EXCEPTION_VALUE_NOT_NUMBER("期望数字类型，实际值: {0}"),
    EXCEPTION_SCRIPT_REGISTER_FAILED("注册脚本失败: {0}"),
    EXCEPTION_EVENT_SCRIPT_CLASS_NOT_FOUND("注册事件脚本失败: 未找到对应的类: {0}"),
    EXCEPTION_EVENT_SCRIPT_NOT_EVENT("注册事件脚本失败: {0} 不是一个事件类"),
    EXCEPTION_EVENT_SCRIPT_FAILED("注册事件脚本失败: {0}"),
    EXCEPTION_SCRIPT_CLASS_NOT_FOUND("脚本工具未找到对应的类：{0}"),
    EXCEPTION_SCRIPT_EXECUTE_FAILED("执行脚本失败: {0}"),

    // ========== 踢出/网络/资源 ==========
    KICK_VERIFY_FAILED("&c客户端拓展校验失败"),
    KICK_ILLEGAL_PACKET("非法数据包"),
    EXCEPTION_CONFIG_MIGRATION("配置迁移"),
    EXCEPTION_HTTP_REQUEST_FAILED("HTTP {0}: 请求失败"),
    EXCEPTION_API_ERROR("API错误: {0}"),
    EXCEPTION_RESPONSE_EMPTY("响应数据为空"),
    EXCEPTION_UNKNOWN("未知错误"),

    // ========== 许可证 ==========
    LICENSE_WELCOME("&a欢迎回来, {0}!"),
    LICENSE_VALIDATE_FAILED("&c许可证验证失败: {0}"),
    LICENSE_INFO_NOTE("&a注：该消息为提示消息，不会限制实际功能"),
    LICENSE_EULA_REQUIRED("&c您需要接受EULA协议才可在正式环境使用本软件"),
    LICENSE_EULA_LINK("&c请前往&f https://arcartx.com/resources/eula/view &c查看EULA协议获取许可证"),

    ;




    override fun getEnumMessage(): String{
        return message
    }

    override fun setEnumMessage(message: String) {
        this.message = message
    }

    override fun getEnumName(): String{
        return name
    }

}


val language = ArcartXLanguageManager(bukkitPlugin, AXLanguageKey::class.java)

fun L(languageKey: AXLanguageKey, vararg args: String): String {
    return language.getMessage(languageKey, *args)
}


